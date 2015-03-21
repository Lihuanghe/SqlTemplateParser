package com.lihuanghe;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 非线程安全，每次解析都要 new一个新对象
 * 
${paramter}  -- 必选参数
$[paramter: and column = ${paramter} ]  --可选参数,如果paramter值为空，语句不起作用
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
	public SqlTemplateParser(InputStream in, String charset,
			Map<String, Object> param) throws UnsupportedEncodingException {
		this.in = new PushbackReader(new InputStreamReader(in, charset));
		this.param = param;
		this.sqlbuf = new StringBuffer();
	}

	private PushbackReader in;
	private Map<String, Object> param;
	private StringBuffer sqlbuf;
	private List<String> pstsParam;
	private int curChar = -1;
	private int prechar = -1;
	private ParamType paramtype;

	public class SqlParseException extends RuntimeException {
		static final long serialVersionUID = -7034897190745766939L;
	}

	private enum ParamType {
		String, Array, ConcatString
	}

	/**
	 * @param pstsParam
	 *            用来存储解析之后的参数
	 * @return sql 解析完成的sql
	 */
	public String parseString(List<String> pstsParam) throws SqlParseException,
			IOException {
		this.pstsParam = pstsParam;
		statmentUntilEnd();
		return sqlbuf.toString();
	}

	protected void statmentUntilEnd() throws IOException {
		while ((curChar = readandsavepre()) != -1) {
			statment();
		}
	}

	protected void statment() throws IOException {
		switch (curChar) {
		case '$':
			paramtype = ParamType.String;
			paramter();
			break;
		case '@':
			paramtype = ParamType.Array;
			paramter();
			break;
		case '#':
			paramtype = ParamType.ConcatString;
			paramter();
			break;
		case '\\':
			sqlbuf.append(escape());
			break;
		default:
			sqlbuf.append((char) curChar);
		}
	}

	protected String escape() throws IOException {
		curChar = readandsavepre();
		StringBuilder escapestr = new StringBuilder();
		switch (curChar) {
		case -1:
			escapestr.append(prechar);
			in.unread(curChar);
			break;
		case '\\':
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

	protected void paramter() throws IOException {
		curChar = readandsavepre();
		switch (curChar) {
		case -1:
			sqlbuf.append((char) prechar);
			in.unread(curChar);
			break;
		case '[':
			optional();
			break;
		case '{':
			required();
			break;
		default:
			sqlbuf.append((char) prechar).append((char) curChar);
		}
	}

	protected void optional() throws IOException {
		// 获取参数名
		String paramName = readUntil(':');

		if (ParamType.String.equals(paramtype)) {
			String tmp = (String) param.get(paramName);
			if (tmp == null || "".equals(tmp)) {
				// 丢弃读取的String
				readUntil(']');
			} else {
				statmentUntil(']');
			}
		} else if (ParamType.Array.equals(paramtype)) {
			Object obj = param.get(paramName);
			Collection<String> set = null;
			if (obj instanceof Collection) {
				set = (Collection<String>) obj;
			} else if (obj instanceof String[]) {
				set = Arrays.asList((String[]) obj);
			}
			if (obj != null && set.size() > 0) {
				statmentUntil(']');
			} else {
				// 丢弃读取的String
				readUntil(']');
			}
		}

	}

	protected void statmentUntil(int until) throws IOException {
		curChar = readandsavepre();
		while (curChar != -1 && curChar != until) {
			statment();
			curChar = readandsavepre();
		}
		if (curChar == -1) {
			in.unread(curChar);
		}
	}

	// 处理必选参数
	protected void required() throws IOException {

		// 获取参数名
		String paramName = readUntil('}');

		if (paramName != null && (!"".equals(paramName))) {
			addpstsParam(paramName);
		}
	}

	private void addpstsParam(String paramName) {
		if (ParamType.String.equals(paramtype)) {
			String tmp = (String) param.get(paramName);
			pstsParam.add(tmp == null ? "" : tmp);
			sqlbuf.append('?');
		} else if (ParamType.Array.equals(paramtype)) {
			Object obj = param.get(paramName);
			Collection<String> set = null;
			if (obj instanceof Collection) {
				set = (Collection<String>) obj;
			} else if (obj instanceof String[]) {
				set = Arrays.asList((String[]) obj);
			}
			if (obj != null && set.size() > 0) {
				for (String p : set) {
					pstsParam.add(p);
					sqlbuf.append('?').append(',');
				}
				sqlbuf.deleteCharAt(sqlbuf.length() - 1);
			} else {
				pstsParam.add("7034897190=NeverUsed=745766939L");
				sqlbuf.append('?');
			}
		} else if (ParamType.ConcatString.equals(paramtype)) {
			String tmp = (String) param.get(paramName);
			sqlbuf.append(tmp == null ? "" : tmp);
		}
	}

	
	
	private String readUntil(int c) throws IOException {
		curChar = readandsavepre();
		StringBuilder optparam = new StringBuilder();
		while (curChar != -1 && curChar != c) {
			switch (curChar) {
			case '\\':
				optparam.append(escape());
				break;
			default:
				optparam.append((char) curChar);
			}
			curChar = readandsavepre();
		}
		if (curChar == -1) {
			in.unread(curChar);
			return "";
		} else {
			return optparam.toString();
		}
	}

	private int readandsavepre() throws IOException {
		prechar = curChar;
		curChar = in.read();
		return curChar;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String sql = " aa @[generated:select * fr\\\\om mytable#{month} where sdf = ${month} or ${month} =''  and id in (@{generated})] a";
		String charset = "UTF-8";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("generated", new String[] { "1", "2", "3" });
		map.put("month", "201503");
		List<String> param = new ArrayList<String>();
		String pstsSql = "";
		try {
			SqlTemplateParser p = new SqlTemplateParser(
					new ByteArrayInputStream(sql.getBytes(charset)), charset,
					map);
			pstsSql = p.parseString(param);

		} catch (UnsupportedEncodingException e) {

			e.printStackTrace();
		} catch (SqlParseException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}
		System.out.println(sql);
		System.out.println(pstsSql);
		System.out.println("================");
		System.out.println("pstsParamSize:"+param.size());
		for (String tmp : param) {
			System.out.print(tmp);
			System.out.print("\t");
		}
	}

}