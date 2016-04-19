package com.lihuanghe;


import java.util.List;

/**
 * @author huanghe.li
 * 保存prepareStatement参数值，及参数位置。
 * 记录位置是为了对缓存友好。可以通过参数位置区分不同缓存
 * 
 * @huanghe.li. 2015-03-25
 * 使用sql语句+参数值做MD5做缓存key以后，就不需要position这个字段了。
 * 如果只使用报表chartID + 参数值的话，就需要position字段。因为可能相同chart不同参数条件，导致参数值相同，但参数位置不同。
 */
public class ParameterPrepareStatement {
	private int position;
	private String paramValue;
	public int getPosition() {
		return position;
	}
	public void setPosition(int position) {
		this.position = position;
	}
	public String getParamValue() {
		return paramValue;
	}
	public void setParamValue(String paramValue) {
		this.paramValue = paramValue;
	}
	public ParameterPrepareStatement(int position, String paramValue) {
		super();
		this.position = position;
		this.paramValue = paramValue;
	}
	@Override
	public String toString() {
		return paramValue;
	}
	
}