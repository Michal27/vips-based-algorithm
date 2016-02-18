package org.fit.vips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fit.layout.impl.BaseOperator;
import org.fit.layout.model.Area;
import org.fit.layout.model.AreaTree;
import org.fit.segm.grouping.AreaImpl;
import org.fit.segm.grouping.op.Separator;
import org.fit.segm.grouping.op.SeparatorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VipsBasedOperator extends BaseOperator
{
	private static Logger log = LoggerFactory.getLogger(VipsBasedOperator.class);
	//protected final String[] paramNames = { "useConsistentStyle", "maxLineEmSpace" };
    //protected final ValueType[] paramTypes = { ValueType.BOOLEAN, ValueType.FLOAT };
	protected final String[] paramNames = {};
    protected final ValueType[] paramTypes = {};
	
    protected List<VipsBasedVisualBlocksDTO> visualBlocksPool = new ArrayList<VipsBasedVisualBlocksDTO>();
    protected List<VipsBasedSeparator> detectedSeparators = new ArrayList<VipsBasedSeparator>();
    private final int startLevel = 0;
	
	@Override
    public String getId()
    {
        return "FitLayout.Vips.VipsBased";
    }
    
    @Override
    public String getName()
    {
        return "VIPS";
    }

    @Override
    public String getDescription()
    {
        return "..."; //TODO
    }

    @Override
    public String[] getParamNames()
    {
        return paramNames;
    }

    @Override
    public ValueType[] getParamTypes()
    {
        return paramTypes;
    }
    
    //----------------------------------------------------
    
    @Override
    public void apply(AreaTree atree)
    {
        performVipsAlgorithm((AreaImpl) atree.getRoot());
    }

    @Override
    public void apply(AreaTree atree, Area root)
    {
    	performVipsAlgorithm((AreaImpl) root);
    }
    
    //----------------------------------------------------
    
    protected void performVipsAlgorithm(AreaImpl root)
    {
        //phase of visual block extraction
    	divideDomTree(root, startLevel);
    	
    	//sort detected separators ascending by weight
    	Collections.sort(detectedSeparators, new Comparator<VipsBasedSeparator>(){
    		@Override
    	    public int compare(VipsBasedSeparator sep1, VipsBasedSeparator sep2) {
    	        return Integer.compare(sep1.getWeight(), sep2.getWeight());
    	    }
    	});
    	
    	//phase of content structure construction
    	for (VipsBasedSeparator separator : detectedSeparators) {
			//System.out.println(separator.toString());
    		
    		
		}
    }
    
    private void divideDomTree(AreaImpl root, int currentLevel)
    {
    	//collecting detected separators at actual tree level
    	collectActualSeparators(root);
    	
    	if(dividable(root, currentLevel) && root.getChildCount() != 0) //divide this block //TODO:HERE delete AND statement later
    	{ 
    		for (int i = 0; i < root.getChildCount(); i++)
    			divideDomTree((AreaImpl) root.getChildArea(i), currentLevel++);
    	}
    	else //is a visual block
    	{ 	
    		VipsBasedVisualBlocksDTO visualBlock = new VipsBasedVisualBlocksDTO();
    		
			if(root.getBoxes() != null && root.getBoxes().size() != 0)
				visualBlock.setBlock(root.getBoxes().firstElement());
			else
				visualBlock.setBlock(null);
			visualBlock.setArea(root);
			visualBlock.setDoc(0); //TODO: DoC evaluation of visualBlock
			
			visualBlocksPool.add(visualBlock); //add visual block to pool
			
			/*if(visualBlock.getBlock() != null)
				System.out.println(visualBlock.getBlock().getTagName());*/
			//System.out.println(visualBlock.getVisualBlockArea().getBoxText());
		}
    }
    
    private boolean dividable(AreaImpl root, int currentLevel)
    {
    	if(currentLevel == startLevel) //root is the TOP block
    		return true;
    	else
    		return !isVisualBlock(root);
    }
    
    private boolean isVisualBlock(AreaImpl root)
    {
    	//TODO: apply heuristic rules to root
    	
    	return false;
    }
    
    private void collectActualSeparators(AreaImpl root)
    {
    	VipsBasedSeparatorSet actualLevelSeparators = new VipsBasedSeparatorSet(root);
    	VipsBasedSeparator vipsSeparator = null;
    	
    	for (Separator separator : actualLevelSeparators.getHorizontal())
    	{
    		//System.out.println("Horizontal separator");
    		vipsSeparator = new VipsBasedSeparator(separator);
    		detectedSeparators.add(vipsSeparator);
		}
    	for (Separator separator : actualLevelSeparators.getVertical())
    	{
    		//System.out.println("Vertical separator");
    		vipsSeparator = new VipsBasedSeparator(separator);
    		detectedSeparators.add(vipsSeparator);
		}
    }
}
