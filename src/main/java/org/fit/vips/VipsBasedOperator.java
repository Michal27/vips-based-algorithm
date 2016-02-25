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
    	
    	sortSeparatorsAscending();
    	
    	filterNonVisualSeparators();
    	
    	//phase of content structure construction
    	VipsBasedSeparator actualSeparator = null;	
    	while (detectedSeparators.size() != 0)
    	{
			//System.out.println(separator.toString());
    		
    		actualSeparator = detectedSeparators.get(0);
    		
    		if((actualSeparator.getArea1() != null) && (actualSeparator.getArea2() != null)) //TODO: maybe joining other sibling separators
    		{
    			//merge separator's visual blocks to new node
    			AreaImpl newNode = new AreaImpl(0, 0, 0, 0);
        		newNode.appendChild(actualSeparator.getArea1());
        		newNode.appendChild(actualSeparator.getArea2());
        		
        		if(detectedSeparators.size() == 1)
        		{
        			root = newNode;
        			break;
        		}
        		
        		//for-each separator with same weight
        		for (VipsBasedSeparator sameWeightSeparator : detectedSeparators)
        		{
					if(sameWeightSeparator.getWeight() != actualSeparator.getWeight())
						break;
					
					//for-each child of newNode
					for (Area child : newNode.getChildAreas())
					{
						if((sameWeightSeparator.getArea1() == child) && (sameWeightSeparator.getArea2() != child))
						{
							newNode.appendChild(sameWeightSeparator.getArea2());
							detectedSeparators.remove(sameWeightSeparator);
						}
						else if ((sameWeightSeparator.getArea2() == child) && (sameWeightSeparator.getArea1() != child))
						{
							newNode.appendChild(sameWeightSeparator.getArea1());
							detectedSeparators.remove(sameWeightSeparator);
						}	
					}
				}
        		
        		detectedSeparators.remove(0);
        		
        		//update adjacent areas of remaining separators
        		for (VipsBasedSeparator separator : detectedSeparators)
        		{
        			if((separator.getArea1() == actualSeparator.getArea1()) || (separator.getArea1() == actualSeparator.getArea2()))
        			{
        				separator.setArea1(newNode);
        			}
        			else if ((separator.getArea2() == actualSeparator.getArea1()) || (separator.getArea2() == actualSeparator.getArea2()))
        			{
        				separator.setArea2(newNode);
					}
        		}
    		}
		}
    	
    	processLeafNodes(root);
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
    
    /**
     * Sort detected separators ascending by weight
     */
    private void sortSeparatorsAscending()
    {
    	Collections.sort(detectedSeparators, new Comparator<VipsBasedSeparator>(){
    		@Override
    	    public int compare(VipsBasedSeparator sep1, VipsBasedSeparator sep2) {
    	        return Integer.compare(sep1.getWeight(), sep2.getWeight());
    	    }
    	});
    }
    
    /**
     * Check every detected separator if it separates only visual blocks
     */
    private void filterNonVisualSeparators()
    {
    	boolean isArea1Visual;
    	boolean isArea2Visual;
    	
    	for (VipsBasedSeparator separator : detectedSeparators)
    	{
    		isArea1Visual = false;
    		isArea2Visual = false;
			for (VipsBasedVisualBlocksDTO visualBlock : visualBlocksPool)
			{
				if(separator.getArea1() == visualBlock.getArea())
					isArea1Visual = true;
				else if (separator.getArea2() == visualBlock.getArea())
					isArea2Visual = true;
				
				if(isArea1Visual && isArea2Visual)
					break;
			}
			
			if(isArea1Visual && isArea2Visual)
			{
				//prepare separator for tree reconstruction process
				removeAreasChildNodes(separator);
			}
			else
				detectedSeparators.remove(separator);
		}
    }
    
    private void removeAreasChildNodes(VipsBasedSeparator separator)
    {
    	AreaImpl area1 = separator.getArea1();
    	AreaImpl area2 = separator.getArea2();
    	area1.removeAllChildren();
    	area2.removeAllChildren();
    	separator.setArea1(area1);
    	separator.setArea2(area2);
    }
    
    private AreaImpl processLeafNodes(AreaImpl root)
    {
    	if(root.getChildCount() != 0)
    	{
    		for (int i = 0; i < root.getChildCount(); i++)
    			processLeafNodes((AreaImpl) root.getChildArea(i));
    	}
    	else
    	{
			for (VipsBasedVisualBlocksDTO visualBlock : visualBlocksPool)
			{
				if(root == visualBlock.getArea())
				{
					if(visualBlock.getDoc() < 42)//TODO: Define the PDoC value here instead of 42
						divideDomTree(root, 0);
					break;
				}
			}
		}
    	
    	return root;
    }
}
