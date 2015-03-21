package com.lihuanghe;

import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;

import org.apache.commons.lang.time.DateFormatUtils;

import com.sun.script.javascript.RhinoScriptEngine;

public class ParameterEval {
	private boolean isArray = false;
	private String paramName;
	private String jsCode ;
	
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
		RhinoScriptEngine engine = new RhinoScriptEngine();
		//System.out.println(jsCode);
		CompiledScript compilescript = engine.compile(jsCode);
		Bindings bd = engine.createBindings();
		bd.put(paramName,map.get(paramName));
		bd.put("DateFormat",new DateFormatUtils());
		Object obj = compilescript.eval(bd);
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
