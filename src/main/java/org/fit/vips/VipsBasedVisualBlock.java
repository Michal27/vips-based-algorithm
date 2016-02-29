package org.fit.vips;

import org.fit.layout.model.Box;
import org.fit.segm.grouping.AreaImpl;

public class VipsBasedVisualBlock {
	
	private Box block;
	private AreaImpl area;
	private int doc;
	
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
	
	public void setDoc(int doc) {
		this.doc = doc;
	}
	
	public int getDoc() {
		return doc;
	}
}
