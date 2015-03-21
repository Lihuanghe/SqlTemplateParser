package com.lihuanghe;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
	protected SqlTemplateParser(InputStream in, String charset,
			Map<String, Object> param) throws UnsupportedEncodingException {
		this.in = new PushbackReader(new InputStreamReader(in, charset));
		this.param = param;
	}

	private PushbackReader in;
	private Map<String, Object> param;
	private List<String> pstsParam;
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

	private static SqlTemplateParser createParser(InputStream in, Map map,
			String charset) {
		try {
			return new SqlTemplateParser(in, charset == null ? "utf-8"
					: charset, map);
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
	public static String parseString(String sql, Map map, List<String> pstsParam)
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
	public static String parseString(String sql, Map map,
			List<String> pstsParam, String charset) throws SqlParseException,
			IOException {
		InputStream in = null;

		try {
			in = new ByteArrayInputStream(
					sql.getBytes(charset == null ? "utf-8" : charset));
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
	public static String parseString(InputStream in, Map map,
			List<String> pstsParam) throws SqlParseException, IOException {
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
	public static String parseString(InputStream in, Map map,
			List<String> pstsParam, String charset) throws SqlParseException,
			IOException {
		SqlTemplateParser parser = createParser(in, map, charset);
		return parser.parse(pstsParam);
	}

	/**
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 */
	protected String parse(List<String> pstsParam) throws SqlParseException,
			IOException {
		this.pstsParam = pstsParam;
		return statmentUntilEnd();
	}

	protected String statmentUntilEnd() throws IOException, SqlParseException {
		StringBuilder sqlbuf = new StringBuilder();
		while ((curChar = readandsavepre()) != -1) {
			sqlbuf.append(statment());
		}
		return sqlbuf.toString();
	}

	protected String statment() throws IOException, SqlParseException {

		switch (curChar) {
		case '$':
			paramtype = ParamType.String;
			return paramter();
		case '@':
			paramtype = ParamType.Array;
			return paramter();
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

	protected String paramter() throws IOException, SqlParseException {
		curChar = readandsavepre();
		switch (curChar) {
		case -1:
			return String.valueOf((char) prechar);
		case '[':
			return optionalParameter();
		case '{':
			return requiredParameter();
		default:
			return new StringBuilder().append((char) prechar)
					.append((char) curChar).toString();
		}
	}

	protected String optionalParameter() throws IOException, SqlParseException {
		// 获取参数
		ParameterEval paramName = readParamerUtil(':');

		if (paramName.isArray()) {
			Object obj = paramName.getValueFromMap(param);

			if (obj == null) {
				// 丢弃读取的String
				readUntil(']', false);
				return "";
			}

			Collection<String> set = null;
			if (obj instanceof Collection) {
				set = (Collection<String>) obj;
			} else if (obj instanceof String[]) {
				set = Arrays.asList((String[]) obj);
			}
			// 如果是集全类型，且长度为0
			if (set != null && set.size() == 0) {
				// 丢弃读取的String
				readUntil(']', false);
				return "";
			}
			String statement = statmentUntil(']');
			return statement;
		} else {
			Object obj = paramName.getValueFromMap(param);
			String str = String.valueOf(obj == null ? "" : obj);
			if ("".equals(str)) {
				// 丢弃读取的String
				readUntil(']', false);
				return "";
			} else {
				String statement = statmentUntil(']');
				return statement;
			}
		}
	}

	protected String statmentUntil(int until) throws IOException,
			SqlParseException {

		StringBuilder sqlbuf = new StringBuilder();

		curChar = readandsavepre();

		while (curChar != -1 && curChar != until) {
			sqlbuf.append(statment());
			curChar = readandsavepre();
		}
		if (curChar == -1) {
			throw new SqlParseException("\texpect '" + (char) until + "'");
		}
		return sqlbuf.toString();
	}

	// 处理必选参数
	protected String requiredParameter() throws IOException, SqlParseException {

		// 获取参数名
		ParameterEval paramName = readParamerUtil('}');
		return addpstsParam(paramName);
	}

	private String addpstsParam(ParameterEval paramName) {
		StringBuilder sqlbuf = new StringBuilder();

		Object obj = paramName.getValueFromMap(param);
		if (obj == null) {
			return "";
		}

		Collection<String> set = null;
		if (obj instanceof Collection) {
			set = (Collection<String>) obj;
		} else if (obj instanceof String[]) {
			set = Arrays.asList((String[]) obj);
		}

		// 如果不是集合类型.
		if (set == null) {
			pstsParam.add(String.valueOf(obj));
			return "?";
		}

		if (set != null && set.size() > 0) {
			for (String p : set) {
				pstsParam.add(p);
				sqlbuf.append('?').append(',');
			}
			sqlbuf.deleteCharAt(sqlbuf.length() - 1);
			return sqlbuf.toString();
		} else // 集合为空
		{
			// do Nothing
			return "";
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
			paramName = readParamerUtil('}');
			break;
		default:
			sqlbuf.append((char) prechar).append((char) curChar);
		}

		// 已获取解析后的参数名
		if(paramName!=null){
			String tmp = (String) paramName.getValueFromMap(param);
			sqlbuf.append((tmp == null ? "" : tmp));
		}
		return sqlbuf.toString();
	}

	private String readUntil(int c, boolean isparseParamName)
			throws IOException, SqlParseException {
		curChar = readandsavepre();
		StringBuilder strbuf = new StringBuilder();
		while (curChar != -1 && curChar != c) {

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
					" :position[" + (sqlpos - strbuf.length()) + "]\t expect '"
							+ (char) c + "'").toString());
		} else {
			return strbuf.toString();
		}
	}

	private String parseParameter() throws SqlParseException, IOException {
		switch (curChar) {
		case '\\':
			return escape();
		case '#':
			return parseConcat();
		default:
			return String.valueOf((char) curChar);
		}
	}

	private ParameterEval readParamerUtil(int c) throws IOException,
			SqlParseException {
		curChar = readandsavepre();
		StringBuilder strbuf = new StringBuilder();
		while (curChar != -1 && curChar != '|' && curChar != c) {
			strbuf.append(parseParameter());
			curChar = readandsavepre();
		}
		// 参数名为空
		if (strbuf.length() == 0 || "".equals(strbuf.toString())) {
			throw new SqlParseException(" after \"" + (char) prechar
					+ (char) curChar + "\", paramName is null at position : "
					+ sqlpos);
		}

		if (curChar == -1) {
			throw new SqlParseException(strbuf.append(
					" :position[" + (sqlpos - strbuf.length()) + "]\t expect '"
							+ (char) c + "'").toString());
		} else if (curChar == '|') {
			String name = strbuf.toString();
			// 读取filter内容，应该是一段可执行的js脚本
			String jsCode = readUntil(c, true);
			return new ParameterEval(name, jsCode,
					ParamType.Array.equals(paramtype));
		} else {
			String name = strbuf.toString();
			return new ParameterEval(name, null,
					ParamType.Array.equals(paramtype));
		}
	}

	private int readandsavepre() throws IOException {
		prechar = curChar;
		curChar = in.read();
		sqlpos++;
		return curChar;
	}

}