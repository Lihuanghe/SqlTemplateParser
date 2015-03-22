package com.lihuanghe;

import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang.time.DateFormatUtils;

public class ParameterEval {
	private boolean isArray = false;
	private String paramName;
	private String jsCode ;
	private static final ScriptEngineManager manager = new ScriptEngineManager();
	
	/**
	 *使用静态类型， JavaScriptEngine在jdk1.6的RhinoScriptEngine实现保证线程安全.
	 */
	private static final ScriptEngine engine = manager.getEngineByName("js");
	
	public ParameterEval(String name,String code,boolean isArray)
	{
		this.isArray = isArray;
		this.paramName = name;
		this.jsCode = code;
	}
	public boolean isArray() {
		return isArray;
	}
	
	private Object executeJS(Map map) throws ScriptException
	{
		Bindings bd = engine.createBindings();
		bd.put(paramName,map.get(paramName));
		bd.put("DateFormat",new DateFormatUtils());
		Object obj = engine.eval(jsCode,bd);
		return obj;
	}
	
	public Object getValueFromMap(Map map)
	{
		if(jsCode!=null)
		{
			try {
				return executeJS(map);
			} catch (ScriptException e) {
				e.printStackTrace();
				return null;
			}
		}
		return map.get(paramName);
	}
}
