package org.fit.vips;

import org.fit.layout.model.Box;
import org.fit.layout.model.Rectangular;
import org.fit.segm.grouping.AreaImpl;
import org.fit.segm.grouping.op.Separator;

public class VipsBasedSeparator extends Separator {
	
	public VipsBasedSeparator(short type, int x1, int y1, int x2, int y2)
	{
        super(type, x1, y1, x2, y2);
	}

	public VipsBasedSeparator(Separator orig)
	{
        super(orig);
	}
	
    public VipsBasedSeparator(short type, Rectangular rect)
    {
        super(type, rect);
    }
    
    @Override
    public int getWeight()
	{
	    int result = 0;
	    
	    if(getArea1() == null || getArea2() == null)
	    	return 0;
    	
    	//line joining separators
    	if(getType() == Separator.VERTICAL && getWidth() < 15) 
    		return 0;
    	
    	//If the differences of font properties such as font size and font weight are bigger on two sides of the separator, the weight will be increased
		result += Math.abs(getArea1().getFontSize() - getArea2().getFontSize()); 
		result += Math.abs(getArea1().getFontWeight() - getArea2().getFontWeight()); 
		result += Math.abs(getArea1().getFontStyle() - getArea2().getFontStyle()); 
		result += Math.abs(getArea1().getUnderline() - getArea2().getUnderline()); 
		result += Math.abs(getArea1().getLineThrough() - getArea2().getLineThrough());
		
		//The weight will be increased if the font size of the block above the separator is smaller than the font size of the block below the separator.
		if(getType() == Separator.HORIZONTAL && (getArea1().getFontSize() < getArea2().getFontSize()))
			result += getArea2().getFontSize()-getArea1().getFontSize();
		
		//If background colors of the blocks on two sides of the separator are different, the weight will be increased.
		if(getArea1().getBackgroundColor() != null && getArea2().getBackgroundColor() != null)
		{
			if(!getArea1().getBackgroundColor().equals(getArea2().getBackgroundColor()))
				result *= 2;
		}
		else if((getArea1().getBackgroundColor() == null && getArea2().getBackgroundColor() != null) || (getArea1().getBackgroundColor() != null && getArea2().getBackgroundColor() == null))
			result *= 2;
		
		//If the structures of the blocks on the two sides of the separator are very similar (e.g. both are text), the weight of the separator will be decreased.
		if(getArea1().getBoxes().size() != 0 && getArea2().getBoxes().size() != 0)
			if(	(getArea1().getBoxes().firstElement().getType() == Box.Type.TEXT_CONTENT && getArea2().getBoxes().firstElement().getType() == Box.Type.TEXT_CONTENT) ||
				(getArea1().getBoxes().firstElement().getType() == Box.Type.REPLACED_CONTENT && getArea2().getBoxes().firstElement().getType() == Box.Type.REPLACED_CONTENT))
					result /= 2;
		else if(getArea1().getFontSize() == getArea2().getFontSize() || getArea1().getFontStyle() == getArea2().getFontStyle())
			result /= 2;
		
		//The greater the distance between blocks on different side of the separator, the higher the weight.
    	if(getType() == Separator.HORIZONTAL)
    		result += getHeight()/2;
    	else if(getType() == Separator.VERTICAL)
    	{
    		result += getWidth();
    		result *= 2;
    	}
		
    	
    	return result;
	}
}
