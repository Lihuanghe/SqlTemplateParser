package com.lihuanghe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;
import org.junit.Assert;
import org.junit.Test;

import com.lihuanghe.SqlTemplateParser.SqlParseException;


public class SqlTemplateParserTest {

	@Test
	public void testsql() throws SqlParseException, IOException
	{
		String sql = "select * from shops_#{month} \nwhere 1=1 @[Ids: \nand  id in ('Nouse',@{Ids})  ] \nand status = 1";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("Ids", new String[]{"2","3","4"});
		map.put("month", "201503");
		List<String> param = new ArrayList<String>();
		String pstsSql = SqlTemplateParser.parseString(sql, map, param);
		String expect = "select * from shops_201503 \nwhere 1=1  \nand  id in ('Nouse',?,?,?)   \nand status = 1";
		System.out.println(Arrays.toString(param.toArray()));
		System.out.println(expect);
		
		Assert.assertEquals(expect, pstsSql);
		Assert.assertEquals(3, param.size());
		Assert.assertArrayEquals(new String[]{"2" ,"3" ,"4" }, param.toArray());
		
	}
	@Test
	public void testall() throws SqlParseException, IOException
	{
		String sql = "begin ${p1},@{p2},p#{p1},$[p1: midle1:${p1}],\n $[p5: midle2:${p1}],@[p2:midle3:@{p2}],@[p5:midle4:@{p2}],\\${,\\a,\\\\ end$";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "1");
		map.put("p2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql = SqlTemplateParser.parseString(sql, map, param);
		String expect = "begin ?,?,?,?,p1, midle1:?,\n ,midle3:?,?,?,,${,\\a,\\ end$";
		Assert.assertEquals(expect, pstsSql);
		Assert.assertEquals(8, param.size());
		Assert.assertArrayEquals(new String[]{"1" ,"2" ,"3" ,"4" ,"1" ,"2" ,"3" ,"4" }, param.toArray());
	}
	
	//末尾的转义异常
	@Test
	public void testlost4() 
	{
		String sql = "begin  end\\";
		Map<String, Object> map = new HashMap<String, Object>();
		List<String> param = new ArrayList<String>();
		try {
			String pstsSql = SqlTemplateParser.parseString(sql, map, param);
			Assert.assertTrue(false);
		} catch (SqlParseException e) {
			Assert.assertTrue(true);
			e.printStackTrace();
		} catch (IOException e) {
			Assert.assertTrue(false);
			e.printStackTrace();
		}
	}
	
	//末尾缺少符号
	@Test
	public void testlost5() 
	{
		String sql = "begin  $[lost5: asff end";
		Map<String, Object> map = new HashMap<String, Object>();
		List<String> param = new ArrayList<String>();
		try {
			String pstsSql = SqlTemplateParser.parseString(sql, map, param);
			Assert.assertTrue(false);
		} catch (SqlParseException e) {
			Assert.assertTrue(true);
			e.printStackTrace();
		} catch (IOException e) {
			Assert.assertTrue(false);
			e.printStackTrace();
		}
	}
	//参数缺少大括号
	@Test
	public void testlost1() throws SqlParseException, IOException
	{
		String sql = "begin ${p1,@{p2},p#{p1},$[p1: midle1:${p1}], $[p5: midle2:${p1}],@[p2:midle3:@{p2}],@[p5:midle4:@{p2}],\\${,\\a,\\\\ $abc #XY end";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "1");
		map.put("p2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql = SqlTemplateParser.parseString(sql, map, param);
		String expect = "begin ,p1, midle1:?, ,midle3:?,?,?,,${,\\a,\\ $abc #XY end";
		Assert.assertEquals(expect, pstsSql);
		Assert.assertEquals(4, param.size());
		Assert.assertArrayEquals(new String[]{"1" ,"2" ,"3" ,"4" }, param.toArray());
	}
	
	//参数缺少大括号
	@Test
	public void testlost2() throws IOException 
	{
		String sql = "begin ${p1},@{p2},p#{p1},$[p1: midle1:${p1}], $[p5: midle2:${p1}],@[p5:midle3:@{p2}],@[p2:midle4:@{p2],\\${,\\a,\\\\ end";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "1");
		map.put("p2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql=null;
		try {
		    pstsSql = SqlTemplateParser.parseString(sql, map, param);
			Assert.assertTrue(false);
		} catch (SqlParseException e) {
			e.printStackTrace();
			Assert.assertTrue(true);
		}
		Assert.assertEquals((String)null, pstsSql);
	}
	
	//参数名为空
	@Test
	public void testparamNameisNull() throws IOException 
	{
		String sql = "begin ${#{p1}} ${#{p2}} end";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "1");
		map.put("p2", "");
		List<String> param = new ArrayList<String>();
		String pstsSql=null;
		try {
		    pstsSql = SqlTemplateParser.parseString(sql, map, param);
		    System.out.println(pstsSql);
			Assert.assertTrue(false);
		} catch (SqlParseException e) {
			e.printStackTrace();
			Assert.assertTrue(true);
		}
		Assert.assertEquals((String)null, pstsSql);
	}
	
	//参数缺少大括号
	@Test
	public void testlost3() throws IOException 
	{
		String sql = "begin ${p1},@{p2},p#{p1},$[p1: midle1:${p1}], $[p5: midle2:${p1}],@[p5:midle3:@{p2}],@[p2:midle4:@{p2},\\${,\\a,\\\\ end";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "1");
		map.put("p2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql=null;
		try {
		    pstsSql = SqlTemplateParser.parseString(sql, map, param);
			Assert.assertTrue(false);
		} catch (SqlParseException e) {
			e.printStackTrace();
			Assert.assertTrue(true);
		}
		Assert.assertEquals((String)null, pstsSql);
	}
	//参数缺少大括号
	@Test
	public void testparamNameContain() throws IOException 
	{
		String sql = "begin ${p1\n} end";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "1");
		map.put("p2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql=null;
		try {
		    pstsSql = SqlTemplateParser.parseString(sql, map, param);
			Assert.assertTrue(false);
		} catch (SqlParseException e) {
			e.printStackTrace();
			Assert.assertTrue(true);
		}
		Assert.assertEquals((String)null, pstsSql);
	}
	@Test
	public void testconcat() throws SqlParseException, IOException
	{
		String sql = "begin ${p4} ${ p1 },@{\t p2 \t },${p#{p1}\t},#{p#{p1}},$[p1: midle1:${p1},#{p#{p1}} ],\n $[p5: midle2:${p1}],@[p2:midle3:@{p2}],@[p5:midle4:@{p2}],\\${,\\a,\\\\ end#";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("p1", "3");
		map.put("p3", "1");
		map.put("p4", "");
		map.put("p2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql = SqlTemplateParser.parseString(sql, map, param);
		String expect = "begin ? ?,?,?,?,?,1, midle1:?,1 ,\n ,midle3:?,?,?,,${,\\a,\\ end#";
		Assert.assertEquals(expect, pstsSql);
		Assert.assertEquals(10, param.size());
		Assert.assertArrayEquals(new String[]{"","3" ,"2" ,"3" ,"4" ,"1","3" ,"2" ,"3" ,"4" }, param.toArray());
	}
	@Test
	public void testCNcode() throws SqlParseException, IOException
	{
		String sql = "开始 ${参数1},@{参数2},参数#{参数1},$[参数1: 中间1:${参数1}], $[参数5: 中间2:${参数1}],@[参数2:中间3:@{参数2}],@[参数5:midle4:@{参数2}],\\${,\\a,\\\\ 结束";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("参数1", "1");
		map.put("参数2", new String[]{"2","3","4"});
		List<String> param = new ArrayList<String>();
		String pstsSql = SqlTemplateParser.parseString(sql, map, param);
		String expect = "开始 ?,?,?,?,参数1, 中间1:?, ,中间3:?,?,?,,${,\\a,\\ 结束";
		Assert.assertEquals(expect, pstsSql);
		Assert.assertEquals(8, param.size());
		Assert.assertArrayEquals(new String[]{"1" ,"2" ,"3" ,"4" ,"1" ,"2" ,"3" ,"4" }, param.toArray());
	}
	
	//测试js执行
	@Test
	public void testJscode() throws SqlParseException, IOException
	{
		Date d = new Date();
		String sql = "begin ${b3|[]} @[b3|[]: abc ] ${abc|abc&&DateFormat.format(abc,'yyyy-MM-dd')} ${abcd|abcd&&DateFormat.format(abcd,'yyyy-MM-dd')} (@{b3|['hello'].concat(b3.join('*'))}) #{abc|DateFormat.format(abc,'yyyy-MM-dd')} #{beginDate|DateFormat.format(DateUtils.addMonths(DateUtils.parseDate(beginDate, ['yyyy-MM-dd']),-1),'yyyyMM')} (${b2|b2.addAll(['5','6','7']);b2}) end ";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("beginDate","2015-03-23");
		map.put("abc", d);
		map.put("b3", new String[]{"2","3","4"});
		List<String> tmp = new ArrayList<String>();
		tmp.addAll(Arrays.asList(new String[]{"2","3","4"}));
		map.put("b2", tmp);
		
		List<String> param = new ArrayList<String>();
		String pstsSql = SqlTemplateParser.parseString(sql, map, param,"utf-8");
		System.out.println(pstsSql);
		System.out.println(Arrays.toString(param.toArray()));
		
		String str = DateFormatUtils.format(d,"yyyy-MM-dd");
		String expect = "begin   ?  (?,?) "+str+" 201502 (?,?,?,?,?,?) end ";
		Assert.assertEquals(expect, pstsSql);
		Assert.assertEquals(9, param.size());
		Assert.assertArrayEquals(new String[]{str,"hello","2*3*4" ,"2","3","4","5","6","7"}, param.toArray());
	}
	
	//测试js执行性能 
	@Test
	public void testJsperf() throws SqlParseException, IOException
	{
		Date st = new Date();
		int i = 0;
		Date d = new Date();
		String sql = "begin ${abc|DateFormat.format(abc,'yyyy-MM-dd')} #{abc|DateFormat.format(abc,'yyyy-MM-dd')}end ";
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("abc", d);
		List<String> param = new ArrayList<String>();
		for(;i< 1000;i++){
			SqlTemplateParser.parseString(sql, map, param);
		};
		Date ed = new Date();
		System.out.println(ed.getTime() - st.getTime());
		Assert.assertTrue("js执行过慢，单次执行大于10ms",(ed.getTime() - st.getTime())<10000);
	}
}
