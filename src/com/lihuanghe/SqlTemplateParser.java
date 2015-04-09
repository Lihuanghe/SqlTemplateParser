package com.lihuanghe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

/*
 * 非线程安全，每次解析都要 new一个新对象
 * 
 ${paramter#{String}}  -- 必选参数
 $[paramter: and column = ${} ]  --可选参数,如果paramter值为空，语句不起作用
 @{array}  --必选数组展开
 @[array: and id in (@{array})]   --可选数组，如果数组为空，则语句不起作用

 #{String}  --字符串拼接，用于处理动态表名

 */

public class SqlTemplateParser {

	/**
	 * @param in
	 *            输入的原始sql
	 * @param param
	 *            用户输入的参数或者环境变量
	 * @throws UnsupportedEncodingException
	 **/
	protected SqlTemplateParser(InputStream in, String charset, Map<String, Object> param)
			throws UnsupportedEncodingException {
		this.in = new InputStreamReader(in, charset);
		this.param = param;
	}

	private InputStreamReader in;
	private Map<String, Object> param;
	private int curChar = -1;
	private int prechar = -1;
	private int sqlpos = 0; // 用于记录当前读取到哪个字符
	private ParamType paramtype; // 用于区别当前要处理的参数类型是否是数组

	public class SqlParseException extends RuntimeException {

		static final long serialVersionUID = -7034897190745766939L;

		public SqlParseException(String s) {
			super(s);
		}
	}

	private enum ParamType {
		String, Array
	}

	private static SqlTemplateParser createParser(InputStream in, Map map, String charset) {
		try {
			return new SqlTemplateParser(in, charset == null ? "utf-8" : charset, map);
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	/**
	 * @param sql
	 *            待解析的sql
	 * @param map
	 *            存储参数变量
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 */
	public static String parseString(String sql, Map map, List<ParameterPrepareStatement> pstsParam)
			throws SqlParseException, IOException {
		return parseString(sql, map, pstsParam, null);
	}

	/**
	 * @param sql
	 *            待解析的sql
	 * @param map
	 *            存储参数变量
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 */
	public static String parseString(String sql, Map map, List<ParameterPrepareStatement> pstsParam, String charset)
			throws SqlParseException, IOException {
		InputStream in = null;

		try {
			in = new ByteArrayInputStream(sql.getBytes(charset == null ? "utf-8" : charset));
			return parseString(in, map, pstsParam, charset);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {

				}
			}
		}
	}

	/**
	 * @param in
	 *            从InputStream读取sql流
	 * @param map
	 *            存储参数变量
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 */
	public static String parseString(InputStream in, Map map, List<ParameterPrepareStatement> pstsParam)
			throws SqlParseException, IOException {
		return parseString(in, map, pstsParam, null);
	}

	/**
	 * @param in
	 *            从InputStream读取sql流
	 * @param map
	 *            存储参数变量
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 * @throws IOException
	 */
	public static String parseString(InputStream in, Map map, List<ParameterPrepareStatement> pstsParam, String charset)
			throws SqlParseException, IOException {
		SqlTemplateParser parser = createParser(in, map, charset);
		return parser.parse(pstsParam);
	}

	/**
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 */
	protected String parse(List<ParameterPrepareStatement> pstsParam) throws SqlParseException, IOException {
		return statmentUntilEnd(pstsParam);
	}

	protected String statmentUntilEnd(List<ParameterPrepareStatement> pstsParam) throws IOException, SqlParseException {
		StringBuilder sqlbuf = new StringBuilder();
		while ((curChar = readandsavepre()) != -1) {
			sqlbuf.append(statment(pstsParam));
		}
		return sqlbuf.toString();
	}

	protected String statment(List<ParameterPrepareStatement> pstsParam) throws IOException, SqlParseException {

		switch (curChar) {
		case '$':
			paramtype = ParamType.String;
			return paramter(pstsParam);
		case '@':
			paramtype = ParamType.Array;
			return paramter(pstsParam);
		case '#':
			return parseConcat();
		case '\\':
			return escape();
		default:
			return String.valueOf((char) curChar);
		}
	}

	protected String escape() throws IOException, SqlParseException {
		curChar = readandsavepre();
		StringBuilder escapestr = new StringBuilder();
		switch (curChar) {
		case -1:
			throw new SqlParseException("expect escape char '\\' ");
		case '\\':
		case '|':
		case '@':
		case '$':
		case '#':
		case ':':
		case '?':
		case '{':
		case '}':
		case '[':
		case ']':
			escapestr.append((char) curChar);
			break;
		default:
			escapestr.append((char) prechar).append((char) curChar);
		}
		return escapestr.toString();
	}

	protected String paramter(List<ParameterPrepareStatement> pstsParam) throws IOException, SqlParseException {
		curChar = readandsavepre();
		switch (curChar) {
		case -1:
			return String.valueOf((char) prechar);
		case '[':
			return optionalParameter(pstsParam);
		case '{':
			return requiredParameter(pstsParam);
		default:
			return new StringBuilder().append((char) prechar).append((char) curChar).toString();
		}
	}

	private boolean isEmpty(ParameterEval paramName) {
		if (paramName.isArray()) {
			Object obj = paramName.getValueFromMap(param);

			if (obj == null) {
				return true;
			}

			Collection<String> set = null;
			if (obj instanceof Collection) {
				set = (Collection<String>) obj;
			} else if (obj instanceof String[]) {
				set = Arrays.asList((String[]) obj);
			}
			// 如果是集合类型，且长度为0
			if (set != null && set.size() == 0) {
				return true;
			}
			return false;
		} else {
			Object obj = paramName.getValueFromMap(param);
			String str = String.valueOf(obj == null ? "" : obj);
			if (StringUtils.isEmpty(str)) {
				return true;
			} else {
				return false;
			}
		}
	}

	protected String optionalParameter(List<ParameterPrepareStatement> pstsParam) throws IOException, SqlParseException {
		// 获取参数
		ParameterEval paramName = readParamerUntil(new int[] { ':', '?' });
		if (curChar == ':') {
			if (isEmpty(paramName)) {
				statmentUntil(']', new ArrayList());
				// 丢弃statement
				return "";
			} else {
				return statmentUntil(']', pstsParam);
			}
		} else if (curChar == '?') {
			if (isEmpty(paramName)) {
				statmentUntil(':', new ArrayList()); // 不执行的statement
				return statmentUntil(']', pstsParam);
			} else {
				String s = statmentUntil(':', pstsParam);

				statmentUntil(']', new ArrayList()); // 不执行的statement

				return s;
			}
		}
		throw new SqlParseException("something Error. never be here!!");
	}

	protected String statmentUntil(int until, List<ParameterPrepareStatement> pstsParam) throws IOException,
			SqlParseException {

		StringBuilder sqlbuf = new StringBuilder();

		curChar = readandsavepre();

		while (curChar != -1 && curChar != until) {
			sqlbuf.append(statment(pstsParam));
			curChar = readandsavepre();
		}
		
		String str = sqlbuf.toString();
		if (curChar == -1) {
			throw new SqlParseException("\texpect char '" + (char) until + "' after statement : " +str);
		}
		return str;
	}

	// 处理必选参数
	protected String requiredParameter(List<ParameterPrepareStatement> pstsParam) throws IOException, SqlParseException {

		// 获取参数名
		ParameterEval paramName = readParamerUntil(new int[] { '}' });
		return addpstsParam(paramName, pstsParam);
	}

	private String addpstsParam(ParameterEval paramName, List<ParameterPrepareStatement> pstsParam) {
		StringBuilder sqlbuf = new StringBuilder();

		
		Object obj = paramName.getValueFromMap(param);
		
		//如果没有该参数，则忽略
		if(obj == null) return "";
		
		Collection<String> set = null;
		if (obj instanceof Collection) {
			set = (Collection<String>) obj;
		} else if (obj instanceof String[]) {
			set = Arrays.asList((String[]) obj);
		}
		// 如果不是集合类型.
		if (set == null) {
			pstsParam.add(new ParameterPrepareStatement(sqlpos, String.valueOf(obj)));
			return "?";
		} else {
			if (set.size() > 0) {
				for (Object s : set) {
					String p = String.valueOf(s);
					pstsParam.add(new ParameterPrepareStatement(sqlpos, p));
					sqlbuf.append('?').append(',');
				}
				sqlbuf.deleteCharAt(sqlbuf.length() - 1);
			}
			return sqlbuf.toString();
		}
	}

	private String parseConcat() throws IOException, SqlParseException {
		curChar = readandsavepre();
		StringBuilder sqlbuf = new StringBuilder();
		ParameterEval paramName = null;
		switch (curChar) {
		case -1:
			sqlbuf.append((char) prechar);
			break;
		case '{':
			paramName = readParamerUntil(new int[] { '}' });
			break;
		default:
			sqlbuf.append((char) prechar).append((char) curChar);
		}

		// 已获取解析后的参数名
		if (paramName != null) {
			String tmp = (String) paramName.getValueFromMap(param);
			if (tmp != null) {
				sqlbuf.append(tmp);
			}
		}
		return sqlbuf.toString();
	}

	private String readUntil(int[] c, boolean isparseParamName) throws IOException, SqlParseException {
		curChar = readandsavepre();
		StringBuilder strbuf = new StringBuilder();
		while (curChar != -1 && search(c, curChar) == -1) {

			if (isparseParamName) {
				strbuf.append(parseParameter());
			} else {
				// 对于要丢弃的字符，不再解析
				strbuf.append((char) curChar);
			}

			curChar = readandsavepre();
		}
		if (curChar == -1) {
			throw new SqlParseException(strbuf.append(
					" :position[" + (sqlpos - strbuf.length()) + "]\t expect {" + Arrays.toString(c) + "}").toString());
		} else {
			return strbuf.toString();
		}
	}

	private String parseParameter() throws SqlParseException, IOException {
		switch (curChar) {
		case '\r':
		case '\n':
			throw new SqlParseException("parameter contains '\\r' ,'\\n' ");
		case '\\':
			return escape();
		case '#':
			return parseConcat();
		default:
			return String.valueOf((char) curChar);
		}
	}

	private int search(int[] chars, int c) {
		if (chars == null || chars.length == 0)
			return -1;
		for (int i = 0; i < chars.length; i++) {
			if (c == chars[i]) {
				return i;
			}
		}
		return -1;
	}

	private ParameterEval readParamerUntil(int[] chars) throws IOException, SqlParseException {
		curChar = readandsavepre();
		StringBuilder strbuf = new StringBuilder();
		while (curChar != -1 && curChar != '|' && search(chars, curChar) == -1) {
			String tmp = parseParameter();
			strbuf.append(tmp);
			curChar = readandsavepre();
		}
		// 去掉参数名的空格
		String name = strbuf.toString().trim();
		// 参数名为空
		if (StringUtils.isEmpty(name)) {
			throw new SqlParseException(" after \"" + (char) prechar + (char) curChar
					+ "\", Parameter Name is null at position : " + sqlpos);
		}

		if (curChar == -1) {
			throw new SqlParseException(strbuf.append(
					" :position[" + (sqlpos - strbuf.length()) + "]\t expect {" + Arrays.toString(chars) + "}")
					.toString());
		} else if (curChar == '|') {

			// 读取filter内容，应该是一段可执行的js脚本
			String jsCode = readUntil(chars, true);
			return new ParameterEval(name, jsCode, ParamType.Array.equals(paramtype));
		} else {
			return new ParameterEval(name, null, ParamType.Array.equals(paramtype));
		}
	}

	private int readandsavepre() throws IOException {
		prechar = curChar;
		curChar = in.read();
		sqlpos++;
		return curChar;
	}

}