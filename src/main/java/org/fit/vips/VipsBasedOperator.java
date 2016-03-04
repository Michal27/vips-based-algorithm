package org.fit.vips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.fit.layout.impl.BaseOperator;
import org.fit.layout.model.Area;
import org.fit.layout.model.AreaTree;
import org.fit.layout.model.Box;
import org.fit.layout.model.Rectangular;
import org.fit.segm.grouping.AreaImpl;
import org.fit.segm.grouping.op.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import weka.core.DistanceFunction;
import ch.qos.logback.classic.Level;

public class VipsBasedOperator extends BaseOperator
{
	private static Logger log = LoggerFactory.getLogger(VipsBasedOperator.class);
    
    protected float pdocValue;
    protected static final int maxPdocValue = 1;
    
	protected final String[] paramNames = { "pdocValue" };
    protected final ValueType[] paramTypes = { ValueType.FLOAT };
	
    protected List<VipsBasedVisualBlock> visualBlocksPool = new ArrayList<VipsBasedVisualBlock>();
    protected List<VipsBasedSeparator> detectedSeparators = new ArrayList<VipsBasedSeparator>();
    private static final int startLevel = 0;
    private boolean isNotValidNode = false;
    private boolean docValueIsKnown = false;
    private float docValue = 0;
    private AreaTree defaultAreaTree = null;
    
    public VipsBasedOperator()
    {
    	pdocValue = maxPdocValue;
    }
    
    public VipsBasedOperator(float pdocValue)
    {
        this.pdocValue = pdocValue;
    }
	
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
    	defaultAreaTree = atree;
        performVipsAlgorithm((AreaImpl) atree.getRoot());
        
    }

    @Override
    public void apply(AreaTree atree, Area root)
    {	
    	/*System.out.println("Leaf Node In Progress: " + root.toString());
    	for (Area child : root.getChildAreas())
		{
			System.out.println(child.toString());
		}*/
    	
    	defaultAreaTree = atree;
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
    	AreaImpl newNode = null;
    	while (detectedSeparators.size() != 0)
    	{
			//System.out.println(detectedSeparators.size());
    		
    		actualSeparator = detectedSeparators.get(0);
    		
    		if((actualSeparator.getArea1() != null) && (actualSeparator.getArea2() != null)) //TODO: maybe joining other sibling separators
    		{
    			//merge separator's visual blocks to new node
    			newNode = new AreaImpl(0, 0, 0, 0);
        		newNode.appendChild(actualSeparator.getArea1());
        		newNode.appendChild(actualSeparator.getArea2());
        		
        		if(detectedSeparators.size() != 0)
        			detectedSeparators.remove(0);
        		
        		List<VipsBasedSeparator> detectedSeparatorsCopy = new ArrayList<VipsBasedSeparator>(detectedSeparators);
        		//for-each separator with same weight
        		while(detectedSeparatorsCopy.size() != 0)
        		{
        			VipsBasedSeparator sameWeightSeparator = detectedSeparatorsCopy.get(0);
        			
					if(sameWeightSeparator.getWeight() != actualSeparator.getWeight())
						break;
					
					//for-each child of newNode
					for (Area child : newNode.getChildAreas())
					{
						if((sameWeightSeparator.getArea1() == child) && (sameWeightSeparator.getArea2() != child))
						{
							newNode.appendChild(sameWeightSeparator.getArea2());
							detectedSeparators.remove(sameWeightSeparator);
							break;
						}
						else if ((sameWeightSeparator.getArea2() == child) && (sameWeightSeparator.getArea1() != child))
						{
							newNode.appendChild(sameWeightSeparator.getArea1());
							detectedSeparators.remove(sameWeightSeparator);
							break;
						}	
					}
					
					detectedSeparatorsCopy.remove(0);
				}
        		
        		//update bounds of the newNode
        		for (int i = 0; i < newNode.getChildCount(); i++)
        		{
        			Area child = newNode.getChildArea(i);
        			Rectangular bounds = newNode.getBounds();
        			
					if(i == 0)
					{
						bounds.setX1(child.getX1());
						bounds.setY1(child.getY1());
						newNode.setBounds(bounds);
					}
					else
					{
						if(child.getX1() < bounds.getX1())
						{
							bounds.setX1(child.getX1());
							newNode.setBounds(bounds);
						}
						if(child.getY1() < bounds.getY1())
						{
							bounds.setX1(child.getX1());
							newNode.setBounds(bounds);
						}
					}
				}
        		
        		//update adjacent areas of remaining separators
        		for (VipsBasedSeparator separator : detectedSeparators)
        		{
        			for (Area child : newNode.getChildAreas())
        			{
        				if(separator.getArea1() == child)
            			{
            				separator.setArea1(newNode);
            			}
            			else if (separator.getArea2() == child)
            			{
            				separator.setArea2(newNode);
    					}
					}
        		}
        		
        		//TODO:comment this output dump lately
        		System.out.println();
        		System.out.println("Remaining separators:");
        		for (VipsBasedSeparator separator : detectedSeparators) {
					System.out.println(separator.toString());
				}
        		printCreatedSubtree(newNode, 0);
    		}
		}
    	
    	//refer actual tree to output
    	root.removeAllChildren();
    	if(newNode != null)
    		root.appendChild(newNode);
    	
    	processLeafNodes(root);
    }
    
    private void printCreatedSubtree(AreaImpl root, int level)
    {
    	if(level == 0)
    	{
    		System.out.println();
    		System.out.println("--------------------------------------------");
    		System.out.println("Root: " + root.toString());
    	}
		if(root.getChildCount() != 0)
    	{
			for (int i = 0; i < level; i++) {
				System.out.print("      ");
			}
			if(level != 0)
				System.out.println("NOT Leaf node:" + root.toString());
			level++;
    		for (int i = 0; i < root.getChildCount(); i++)
    		{
    			printCreatedSubtree((AreaImpl) root.getChildArea(i), level);
    		}
    	}
    	else
    	{
    		for (int i = 0; i < level; i++) {
				System.out.print("      ");
			}
    		System.out.println("Leaf node:" + root.toString());
    	}

	}

	private void divideDomTree(AreaImpl root, int currentLevel)
    {
    	//collecting detected separators at actual tree level
    	collectActualSeparators(root);
    	
    	if(dividable(root, currentLevel)) //divide this block
    	{ 
    		for (int i = 0; i < root.getChildCount(); i++)
    		{
    			reconfigureSeparators(root);
    			divideDomTree((AreaImpl) root.getChildArea(i), currentLevel++);
    		}
    	}
    	else //is a visual block
    	{ 	
    		if(!isNotValidNode)
    		{
    			VipsBasedVisualBlock visualBlock = new VipsBasedVisualBlock();
        		
    			if(root.getBoxes() != null && root.getBoxes().size() != 0)
    				visualBlock.setBlock(root.getBoxes().firstElement());
    			else
    				visualBlock.setBlock(null);
    			
    			visualBlock.setArea(root);
    			visualBlock.setDomNode(root);
    			
    			if(docValueIsKnown)
    			{
    				visualBlock.setDoc(docValue);
    				docValueIsKnown = false;
    			}
    			else
    				visualBlock.setDoc(0f); //TODO: DoC evaluation of visualBlock
    			
    			visualBlocksPool.add(visualBlock); //add visual block to pool
    		}
    		else
    			isNotValidNode = false;
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
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return true;
    	}
    	else if(isMetVipsRule2(root))
    	{
    		return false;
    	}
    	else if (isMetVipsRule3(root))
    	{
			return false;
		}
    	else if (isMetVipsRule4(root))
    	{
			return true;
		}
    	else if (isMetVipsRule5(root))
    	{
    		return false;
		}
    	else//TODO: this is just for testing
    		return false;
    }
    
    private boolean isMetVipsRule1(AreaImpl root)
    {
    	/* If the DOM node is not a text node and it has no valid children, then this node cannot be divided and will be cut. */
			 
    	Box box = root.getBoxes().get(0);
    	
    	//if the DOM node is not a text node
    	if(box.getType() != Box.Type.TEXT_CONTENT)
    	{
    		boolean noValidChild = true;
    		
    		//and it has no valid children
			for (Area child : root.getChildAreas())
    		{
				if(child.getBoxes().get(0).isVisible())
				{
					noValidChild = false;
					break;
				}
			}
			
    		return noValidChild;
    	}
    	else
    		return false;
    }
    
    private boolean isMetVipsRule2(AreaImpl root)
    {	
    	/* If the DOM node has only one valid child and the child is not a text node, then divide this node. */
    	
    	//has only one child
    	if(root.getChildCount() == 1)
    	{
    		Box childNode = root.getChildArea(0).getBoxes().get(0);
    		
    		//the child is Valid
    		if(childNode.isVisible()) 
    			//the child is not a text node
    			if(childNode.getType() != Box.Type.TEXT_CONTENT)
    				return true;
    	}	
		return false;
    }
    
    //TODO: is this rule really needed? Is it possible to know root node of some block? Isn't it a nonsense?
    private boolean isMetVipsRule3(AreaImpl root)
    {
    	/*	
    	 	If the DOM node is the root node of the sub-DOM tree (corresponding to the block),
    		and there is only one sub DOM tree corresponding to this block, divide this node.
    	 */
    	return false;
    }
    
    private boolean isMetVipsRule4(AreaImpl root)
    {	
    	/* 	
			If all of the child nodes of the DOM node are text nodes or virtual text nodes, do not divide the node.  
			If the font size and font weight of all these child nodes are same, set the DoC of the extracted block to 1. 
			Otherwise, set the DoC of this extracted block to 0.9.
		*/
    	
    	float previousNodeWeight = 0;
    	float previousNodeSize = 0;
    	docValue = 0.9f;
    	
    	for (Area child : root.getChildAreas())
    	{
			if(child.getBoxes().get(0).getType() != Box.Type.TEXT_CONTENT) //if child node isn't a text node
			{
				for (Area virtualNodeChild : child.getChildAreas())
				{
					if(virtualNodeChild.getBoxes().get(0).getType() != Box.Type.TEXT_CONTENT) //if child node isn't even a virtual text node
						return false;
				}
			}
			
			//font size and font weight comparison
			if(child == root.getChildAreas().get(0))
			{
				previousNodeWeight = child.getFontWeight();
				previousNodeSize = child.getFontSize();
			}
			else
			{
				if(Float.compare(previousNodeWeight, child.getFontWeight()) != 0)
					if(Float.compare(previousNodeSize, child.getFontSize()) != 0)
						docValue = 1f;
			}
		}
    	
    	docValueIsKnown = true;
    	return true;
    }
    
    private boolean isMetVipsRule5(AreaImpl root)
    {
    	/*	
    	 	If one of the child nodes of the DOM node is line-break node, then divide this DOM node.
    	 */
    	
    	for (Area child : root.getChildAreas())
    	{
    		if(isLineBreakNode(child))
    			return true;
		}
    	
    	return false;
    }
    
    private boolean isLineBreakNode(Area root)
    {
    	String tagName = root.getBoxes().get(0).getTagName();
    	
    	//if the node isn't a inline element
    	if(	//TODO: are these tag names correctly formated?
    		!tagName.equals("b") && !tagName.equals("big") && !tagName.equals("i") && !tagName.equals("small") && 
    		!tagName.equals("tt") && !tagName.equals("abbr") && !tagName.equals("acronym") && !tagName.equals("cite") &&
    		!tagName.equals("code") && !tagName.equals("dfn") && !tagName.equals("em") && !tagName.equals("kbd") &&
    		!tagName.equals("strong") && !tagName.equals("samp") && !tagName.equals("time") && !tagName.equals("var") &&
    		!tagName.equals("a") && !tagName.equals("bdo") && !tagName.equals("br") && !tagName.equals("img") &&
    		!tagName.equals("map") && !tagName.equals("object") && !tagName.equals("q") && !tagName.equals("script") &&
    		!tagName.equals("span") && !tagName.equals("sub") && !tagName.equals("sup") && !tagName.equals("button") &&
    		!tagName.equals("input") && !tagName.equals("label") && !tagName.equals("select") && !tagName.equals("textarea") &&
    		!tagName.equals("u") && !tagName.equals("font")
    	)
    		return true;
    	else
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
    	/*for (VipsBasedSeparator separator : detectedSeparators) {
    		System.out.println(separator.toString());
    		
    		System.out.println("SeparatorArea_BEGIN");
    		if(separator.getArea1() != null)
    			System.out.println(separator.getArea1().toString());
    		if(separator.getArea2() != null)
    			System.out.println(separator.getArea2().toString());
    		System.out.println("SeparatorArea_END");
		}
    	for (VipsBasedVisualBlock block : visualBlocksPool) {
    		System.out.println(block.getArea().toString());
		}*/
    	//make a copy of detectedSeparators
    	ArrayList<VipsBasedSeparator> separators = new ArrayList<VipsBasedSeparator>(detectedSeparators);
    	
    	boolean isArea1Visual;
    	boolean isArea2Visual;	
    	for (VipsBasedSeparator separator : separators)
    	{
    		isArea1Visual = false;
    		isArea2Visual = false;
			for (VipsBasedVisualBlock visualBlock : visualBlocksPool)
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
				removeAreasChildNodes(detectedSeparators.get(detectedSeparators.indexOf(separator)));
			}
			else
				detectedSeparators.remove(separator);
		}
    	/*System.out.println(detectedSeparators.size());
    	for (VipsBasedSeparator vipsBasedSeparator : detectedSeparators) {
    		System.out.println(vipsBasedSeparator.toString());
		}*/
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
    		//System.out.println("This is NOT Leaf node:" + root.toString());
    		for (int i = 0; i < root.getChildCount(); i++)
    			processLeafNodes((AreaImpl) root.getChildArea(i));
    	}
    	else
    	{
    		//System.out.println("This is Leaf node:" + root.toString());
			for (VipsBasedVisualBlock visualBlock : visualBlocksPool)
			{
				if(root == visualBlock.getArea())
				{				
					if(Float.compare(visualBlock.getDoc(), pdocValue) < 0)
					{
						for (Area child : visualBlock.getDomNode().getChildAreas())
						{
							root.appendChild(child);
						}
						
						VipsBasedOperator divideDomTree = new VipsBasedOperator(pdocValue);
						divideDomTree.apply(defaultAreaTree, root);
					}
					break;
				}
			}
		}
    	
    	return root;
    }
    
    private void reconfigureSeparators(AreaImpl root)
    {
    	List<VipsBasedSeparator> associatedSeparators = getAssociatedSeparators(root);
    	Area child = null;
    	Area leastDistantChild = null;
    	int area1Distance = 0;
    	int area2Distance = 0;
    	int shortestDistance = 0;
    	boolean separatorReconfigured = false;
    	boolean area1Reconfigured = false;
    	
    	for (VipsBasedSeparator actualSeparator : associatedSeparators)
    	{
    		//System.out.println(actualSeparator.toString());
    		leastDistantChild = null;
    		area1Distance = 0;
    		area2Distance = 0;
    		shortestDistance = 0;
    		area1Reconfigured = false;
    		separatorReconfigured = false;
			
    		for (int i = 0; i < root.getChildCount(); i++)
			{
				//System.out.println(child.toString());
    			
    			child = root.getChildArea(i);

				if(actualSeparator.getType() == Separator.HORIZONTAL)
				{
					area1Distance = Math.abs(actualSeparator.getY1() - child.getY2());
					area2Distance = Math.abs(actualSeparator.getY2() - child.getY1());
				}
				else if(actualSeparator.getType() == Separator.VERTICAL)
				{System.out.println("Vypocet\n" + actualSeparator.toString() + "\n");
					area1Distance = Math.abs(actualSeparator.getX1() - child.getX2());
					area2Distance = Math.abs(actualSeparator.getX2() - child.getX1());
				}
				
				if(area1Distance < 2) //TODO: this should be applied only on Visual Blocks!!!!!!! Need to implement a method witch will return just VisualBlocks of the currently divided node and then iterate through this VisualBlocks instead of root.getChildAreas().
				{
					separatorReconfigured = true;
					actualSeparator.setArea1((AreaImpl)child);
					break;
				}
				else if(area2Distance < 2)
				{
					separatorReconfigured = true;
					actualSeparator.setArea2((AreaImpl)child);
					break;
				}
				else
				{System.out.println("Neni prilehly\n" + actualSeparator.toString() + "\n");		
					if(i == 0) //first child
					{
						leastDistantChild = child;
						if(area1Distance < area2Distance)
						{
							shortestDistance = area1Distance;
							area1Reconfigured = true;
						}
						else
						{
							shortestDistance = area2Distance;
							area1Reconfigured = false;
						}
					}
					else
					{
						if(area1Distance < area2Distance)
						{
							if(area1Distance < shortestDistance)
							{
								leastDistantChild = child;
								shortestDistance = area1Distance;
								area1Reconfigured = true;
							}
						}
						else
						{
							if(area2Distance < shortestDistance)
							{
								leastDistantChild = child;
								shortestDistance = area2Distance;
								area1Reconfigured = false;
							}
						}
					}
				}
			}
    		if(!separatorReconfigured)
    		{System.out.println("Nastavi se na \n" + actualSeparator.toString() + "\n" + leastDistantChild.toString() + "\n");
    			if(area1Reconfigured)
    			{
    				actualSeparator.setArea1((AreaImpl)leastDistantChild);
    			}
    			else
    			{
    				actualSeparator.setArea2((AreaImpl)leastDistantChild);
				}
    		}
		}
    }
    
    private List<VipsBasedSeparator> getAssociatedSeparators(AreaImpl node)
    {
    	List<VipsBasedSeparator> result = new ArrayList<VipsBasedSeparator>();
    	
    	for (VipsBasedSeparator separator : detectedSeparators)
    	{
			if((separator.getArea1() == node) || (separator.getArea2() == node))
				result.add(separator);
		}
    	
    	return result;
    }
}
