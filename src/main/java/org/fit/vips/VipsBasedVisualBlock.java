package org.fit.vips;

import org.fit.layout.model.Area;
import org.fit.layout.model.Box;
import org.fit.segm.grouping.AreaImpl;

public class VipsBasedVisualBlock {
	
	private Box block;
	private AreaImpl area;
	private float doc;
	private AreaImpl domNode;
	
	public void setBlock(Box visualBlock)
	{
		this.block = visualBlock;
	}
	
	public Box getBlock()
	{
		return block;
	}
	
	public void setArea(AreaImpl visualBlockArea)
	{
		this.area = visualBlockArea;
	}
	
	public AreaImpl getArea()
	{
		return area;
	}
	
	public void setDoc(float doc)
	{
		this.doc = doc;
	}
	
	public float getDoc()
	{
		return doc;
	}
	
	public void setDomNode(AreaImpl visualBlockDomNode)
	{
		this.domNode = new AreaImpl(visualBlockDomNode);
		copyChildAreas(this.domNode, visualBlockDomNode);
	}
	
	private void copyChildAreas(AreaImpl target, AreaImpl source)
	{
		AreaImpl newChild = null;
		for (Area child : source.getChildAreas())
		{
			newChild = new AreaImpl((AreaImpl)child);
			target.appendChild(newChild);
			copyChildAreas(newChild, (AreaImpl)child);
		}
	}
	
	public AreaImpl getDomNode()
	{
		return domNode;
	}
}
