package org.fit.vips;

import org.fit.layout.model.Rectangular;
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
    
    /*@Override
    public int getWeight()
	{
	    //TODO Implement evaluation of separators Weight
    	return 0;
	}*/
}
