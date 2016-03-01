package org.fit.vips;

import org.fit.layout.model.Box;
import org.fit.segm.grouping.AreaImpl;

public class VipsBasedVisualBlock {
	
	private Box block;
	private AreaImpl area;
	private float doc;
	
	public void setBlock(Box visualBlock) {
		this.block = visualBlock;
	}
	
	public Box getBlock() {
		return block;
	}
	
	public void setArea(AreaImpl visualBlockArea) {
		this.area = visualBlockArea;
	}
	
	public AreaImpl getArea() {
		return area;
	}
	
	public void setDoc(float doc) {
		this.doc = doc;
	}
	
	public float getDoc() {
		return doc;
	}
}
