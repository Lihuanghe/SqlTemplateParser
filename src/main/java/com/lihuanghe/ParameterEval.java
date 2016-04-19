package com.lihuanghe;

import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.commons.lang.time.DateUtils;

public class ParameterEval {
	private boolean isArray = false;
	private String[] paramName;
	private String jsCode ;
	private static final ScriptEngineManager manager = new ScriptEngineManager();
	private static final DateFormatUtils dataformat = new DateFormatUtils();
	private static final DateUtils dateutils = new DateUtils();
	
	/**
	 *使用静态类型， JavaScriptEngine在jdk1.6的RhinoScriptEngine实现保证线程安全.
	 */
	private static final ScriptEngine engine = manager.getEngineByName("js");
	
	public ParameterEval(String name,String code,boolean isArray)
	{
		this.isArray = isArray;
		this.paramName = name.split("\\.");
		this.jsCode = code;
	}
	public boolean isArray() {
		return isArray;
	}
	
	private Object executeJS(Map map) throws ScriptException
	{
		Bindings bd = engine.createBindings();
		bd.put(paramName[paramName.length-1],getparaValue(map));
		bd.put("DateFormat",dataformat);
		bd.put("DateUtils",dateutils);
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
		return getparaValue(map);
	}
	
	private Object getparaValue(Map map){
		if(paramName.length == 1){
			return map.get(paramName[0]);
		}else{
			Map value = map;
			for(int i =0;i<paramName.length;i++){
				Object item = value.get(paramName[i]);
				if(item == null)  return "";
				
				if( i == paramName.length-1){
					return value.get(paramName[i]);
				}else{
					if(item instanceof Map){
						value = (Map)item;
					}else{
						//TODO 不处理不是Map里的对象，可能是一个普通的Bean,要通过反射获取字段值
						return null;
					}
				}
			}
			//never here
			return null;
			
		}
	}
}