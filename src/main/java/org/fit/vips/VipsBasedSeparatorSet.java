/**
 * VipsBasedSeparatorSet.java
 */
package org.fit.vips;

import org.fit.layout.model.Area;
import org.fit.segm.grouping.AreaImpl;
import org.fit.segm.grouping.op.SeparatorSetHVS;

/**
 * Visual separator set from VIPS perspective
 * @author Michal Malanik
 */
public class VipsBasedSeparatorSet extends SeparatorSetHVS{
	
	/**
     * Creates a new separator set with one horizontal and one vertical separator.
     */
    public VipsBasedSeparatorSet(AreaImpl root)
    {
        super(root);
    }

    /**
     * Creates a new separator set with one horizontal and one vertical separator.
     */
    public VipsBasedSeparatorSet(AreaImpl root, Area filter)
    {
        super(root, filter);
    }
    
    @Override
    protected void filterSeparators()
    {
    	//There are all separators needed in VIPS based operator, any separators is filtered out.
    }
}
