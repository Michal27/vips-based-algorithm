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
import org.openrdf.query.algebra.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joestelmach.natty.generated.DateParser_NumericRules.int_00_to_23_optional_prefix_return;

public class VipsBasedOperator extends BaseOperator
{
	private static Logger log = LoggerFactory.getLogger(VipsBasedOperator.class);
    
    protected float pdocValue;
    protected static final int maxPdocValue = 1;
    
	protected final String[] paramNames = { "pdocValue" };
    protected final ValueType[] paramTypes = { ValueType.FLOAT };
	
    protected List<VipsBasedVisualBlock> visualBlocksPool = new ArrayList<VipsBasedVisualBlock>();
    protected List<VipsBasedSeparator> detectedSeparators = new ArrayList<VipsBasedSeparator>();
    private List<AreaImpl> nonDividableNodes = new ArrayList<AreaImpl>();
    private static final int startLevel = 0;
    private boolean isNotValidNode = false;
    private boolean docValueIsKnown = false;
    private float docValue = 0;
    
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
    
    public float getPdocValue()
    {
        return pdocValue;
    }

    public void setPdocValue(float pdocValue)
    {
        this.pdocValue = pdocValue;
    }
    
    public List<VipsBasedVisualBlock> getVisualBlocksPool() {
		return visualBlocksPool;
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
    	visualBlocksPool.clear();
    	detectedSeparators.clear();
    	isNotValidNode = false;
    	
    	collectSeparators(root);

        //phase of visual block extraction
    	divideDomTree(root, startLevel);
    	
    	joinLineVisualBlocks();
    	
    	sortSeparatorsAscending();
    	
    	filterNonVisualSeparators();
    	
    	//phase of content structure construction
    	contentStructureConstruction(root);
    	
    }
    
    private void joinLineVisualBlocks()
    {
    	AreaImpl firstArea = null;
    	AreaImpl secondArea = null;
    	VipsBasedSeparator lineSeparator = null;
    	
		for (VipsBasedVisualBlock firstVisualBlock : visualBlocksPool)
		{
			for (VipsBasedVisualBlock secondVisualBlock : visualBlocksPool)
			{
				firstArea = firstVisualBlock.getArea();
				secondArea = secondVisualBlock.getArea();
				
				if((Math.abs(firstArea.getX2() - secondArea.getX1()) < 2) && (Math.abs(firstArea.getY1() - secondArea.getY1()) < 6) && (Math.abs(firstArea.getY2() - secondArea.getY2()) < 6))
				{
					lineSeparator = new VipsBasedSeparator(Separator.VERTICAL, firstArea.getX2(), firstArea.getY1(), secondArea.getX1(), firstArea.getY2());
					lineSeparator.setArea1(firstArea);
					lineSeparator.setArea2(secondArea);
					detectedSeparators.add(lineSeparator);
				}
			}
		}
		
	}

	private void contentStructureConstruction(AreaImpl root)
    {
    	List<AreaImpl> createdSubtrees = new ArrayList<AreaImpl>();
    	List<AreaImpl> rootChilds = new ArrayList<AreaImpl>();
    	
    	for (VipsBasedVisualBlock visualBlock  : visualBlocksPool)
    	{
			createdSubtrees.add(visualBlock.getArea());
		}
    	
    	VipsBasedSeparator actualSeparator = null;
    	AreaImpl newNode = null;
    	while (detectedSeparators.size() != 0)
    	{
			//System.out.println(detectedSeparators.size());
    		
    		actualSeparator = detectedSeparators.get(0);
    		
    		if((actualSeparator.getArea1() != null) && (actualSeparator.getArea2() != null) && (actualSeparator.getArea1() != actualSeparator.getArea2()))
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
						if((sameWeightSeparator.getArea1() == child) && (sameWeightSeparator.getArea2() != child) && (actualSeparator.getType() == sameWeightSeparator.getType()))
						{
							newNode.appendChild(sameWeightSeparator.getArea2());
							detectedSeparators.remove(sameWeightSeparator);
							break;
						}
						else if ((sameWeightSeparator.getArea2() == child) && (sameWeightSeparator.getArea1() != child) && (actualSeparator.getType() == sameWeightSeparator.getType()))
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
							bounds.setY1(child.getY1());
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
        		
        		createdSubtrees.add(newNode);
        		for (Area child : newNode.getChildAreas())
        		{
        			if(createdSubtrees.contains(child))
            			createdSubtrees.remove(child);
				}
        		
        		/*System.out.println();
        		System.out.println("Remaining separators:");
        		for (VipsBasedSeparator separator : detectedSeparators) {
        			//if(separator.getType() == Separator.VERTICAL)
					System.out.println(separator.toString());
				}
        		printCreatedSubtree(newNode, 0);*/
    		}
		}
    	
    	//append unused subtrees to final tree
    	createdSubtrees.remove(newNode);
    	if(createdSubtrees.size() != 0 && newNode != null)
    		rootChilds = processUnusedSubtrees(newNode, createdSubtrees);
    	
    	//refer actual tree to output
    	root.removeAllChildren();
    	if(newNode != null)
    		root.appendChild(newNode);
    	if(rootChilds.size() != 0)
    	{
    		for (AreaImpl child : rootChilds)
			{
				root.appendChild(child);
			}
    	}
    	
    	//if granularity condition isn't met, further divide leaf nodes.
    	processLeafNodes(root);
    }
    
    private List<AreaImpl> processUnusedSubtrees(AreaImpl root, List<AreaImpl> subtrees)
    {
    	Collections.reverse(subtrees);
    	AreaImpl lastIntersectingArea = null;
    	List<AreaImpl> result = new ArrayList<AreaImpl>();
    	
		for (AreaImpl subtree : subtrees)
		{
			lastIntersectingArea = getLastIntersectingArea(root, subtree);
			if(lastIntersectingArea != null)
				lastIntersectingArea.appendChild(subtree);
			else
				result.add(subtree);
		}
		return result;
	}

	private AreaImpl getLastIntersectingArea(AreaImpl root, AreaImpl area)
	{
		Area child = null;
		AreaImpl tmpResult = null;
		AreaImpl result = null;
		
		for (int i = 0; i < root.getChildCount(); i++)
		{
			child = root.getChildArea(i);
			if((tmpResult = getLastIntersectingArea((AreaImpl)child, area)) != null)
				result = tmpResult;
		}
		
		if(result == null)
		{
			if((root.getBounds().encloses(area.getBounds())))
			{
				return root;
			}
			else
			{
				return null;
			}
		}
		else
		{
			return result;
		}
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
    	if(dividable(root, currentLevel)) //divide this block
    	{ 
    		if(!isNotValidNode)
    		{
	    		for (int i = 0; i < root.getChildCount(); i++)
	    		{
	    			reconfigureSeparators(root);
	    			divideDomTree((AreaImpl) root.getChildArea(i), ++currentLevel);
	    		}
    		}
    		else
    			isNotValidNode = false;
    	}
    	else //is a visual block
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
			{
				//this is for Vips rule 7
				for (AreaImpl nonDividableNode : nonDividableNodes)
				{
					if(nonDividableNode == root)
					{
						visualBlock.setDoc(nonDividableNodeDocEvaluation(root));
						visualBlocksPool.add(visualBlock);
						return;
					}
				}
				
				visualBlock.setDoc(docEvaluation(root));
			}
			
			visualBlocksPool.add(visualBlock); //add visual block to pool
		}
    }
    
    private float docEvaluation(AreaImpl root) {
    	//TODO: DoC evaluation of visualBlock
		return 0;
	}

	private float nonDividableNodeDocEvaluation(AreaImpl root) {
		// TODO evaluate Doc of NonDividableBlock (Vips rule 7)
		return 0;
	}

	private boolean dividable(AreaImpl root, int currentLevel)
    {
    	if(currentLevel == startLevel) //root is the TOP block
    		return true;
    	else
    	{
    		for (AreaImpl nonDividableNode : nonDividableNodes)
    		{
				if(root == nonDividableNode)
					return false;
			}
    		return !isVisualBlock(root);
    	}
    }
    
    private boolean isVisualBlock(AreaImpl root)
    {
    	//TODO: apply advanced heuristic rules to root
    	
    	String tagName = null;
    	if(root.getBoxes().size() != 0)
    		tagName = root.getBoxes().get(0).getTagName();
    	
    	if(isMetImprovedVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	
    	if(isInlineNode(root))
    		return isVisualInline(root);
    	else if(tagName != null && tagName.equals("table"))
    		return isVisualTable(root);
    	else if(tagName != null && tagName.equals("tr"))
    		return isVisualTr(root);
    	else if(tagName != null && tagName.equals("td"))
    		return isVisualTd(root);
    	else if(tagName != null && tagName.equals("p"))
    		return isVisualP(root);
    	else
    		return isVisualOther(root);
    }
    
    private boolean isMetImprovedVipsRule1(AreaImpl root)
    {
		/*
		  	If one of the child nodes has bigger font size than its previous siblings, divide node
			into two blocks. Put the nodes before the child node with bigger font size into the
			first block, and put the remaining nodes to the second block.
		 */
    	Area child = null;
    	
    	for (int i = 0; i < root.getChildCount(); i++)
    	{
    		child = root.getChildArea(i);
    		
    		//first child doesn't have previous sibling
			if(child.getPreviousSibling() != null)
			{
				//if child and previous sibling child are both text nodes
				if((isTextNode(child) && isTextNode(child.getPreviousSibling())) || (isVirtualTextNode(child) && isVirtualTextNode(child.getPreviousSibling())))
				{
					if(child.getFontSize() > child.getPreviousSibling().getFontSize())
					{
						AreaImpl newNode1 = new AreaImpl(0,0,0,0);
						AreaImpl newNode2 = new AreaImpl(0,0,0,0);
						newNode1.addBox(root.getBoxes().get(0));
						newNode2.addBox(root.getBoxes().get(0));//TODO: reimplement this like that: the pair of two new nodes will be not added like next siblings of root, but will be the only childs of root and then the root proceeding will continue
						
						for (int j = 0; j < root.getChildCount(); j++)
						{
							if(j < i)
							{
								System.out.println("TADY1");
								newNode1.appendChild(root.getChildArea(j).copy());
							}
							else
							{
								System.out.println("TADY2");
								newNode2.appendChild(root.getChildArea(j).copy());
							}
						}
						
						root.getParentArea().insertChild(newNode1, root.getParentArea().getIndex(root)+1);
						root.getParentArea().insertChild(newNode2, root.getParentArea().getIndex(root)+2);
						
						System.out.println("SPLITING THIS NODE:" + root + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
						printCreatedSubtree((AreaImpl)root.getParentArea(), 0);
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean isTextNode(Area node)
	{
		if(node.getBoxes().get(0).getType() == Box.Type.TEXT_CONTENT)
			return true;
		else
			return false;
	}

	private boolean isVirtualTextNode(Area node)
	{
		for (Area child : node.getChildAreas())
		{
			if(child.getBoxes().get(0).getType() != Box.Type.TEXT_CONTENT)
				return false;
		}
		return true;
	}

	private boolean isVisualInline(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	else if(isMetVipsRule2(root))
    		return false;
    	else if(isMetVipsRule3(root))
			return false;
    	else if(isMetVipsRule4(root))
			return true;
    	else if(isMetVipsRule5(root))
    		return false;
    	else if(isMetVipsRule6(root))
    		return false;
    	else if(isMetVipsRule8(root))
    		return true;
    	else if(isMetVipsRule9(root))
    		return true;
    	else if(isMetVipsRule11(root))
    		return false;
    	else
    		return false;
	}
    
    private boolean isVisualTable(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	else if(isMetVipsRule2(root))
    		return false;
    	else if(isMetVipsRule3(root))
			return false;
    	else if(isMetVipsRule7(root))
    		return false;
    	else if(isMetVipsRule9(root))
    		return true;
    	else if(isMetVipsRule12(root))
    		return true;
    	else
    		return false;
	}
    
    private boolean isVisualTr(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	else if(isMetVipsRule2(root))
    		return false;
    	else if(isMetVipsRule3(root))
			return false;
    	else if(isMetVipsRule7(root))
    		return false;
    	else if(isMetVipsRule9(root))
    		return true;
    	else if(isMetVipsRule12(root))
    		return true;
    	else
    		return false;
	}
    
    private boolean isVisualTd(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	else if(isMetVipsRule2(root))
    		return false;
    	else if(isMetVipsRule3(root))
			return false;
    	else if(isMetVipsRule4(root))
			return true;
    	else if(isMetVipsRule8(root))
    		return true;
    	else if(isMetVipsRule9(root))
    		return true;
    	else if(isMetVipsRule10(root))
    		return true;
    	else if(isMetVipsRule12(root))
    		return true;
    	else
    		return false;
	}
    
    private boolean isVisualP(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	else if(isMetVipsRule2(root))
    		return false;
    	else if(isMetVipsRule3(root))
			return false;
    	else if(isMetVipsRule4(root))
			return true;
    	else if(isMetVipsRule5(root))
    		return false;
    	else if(isMetVipsRule6(root))
    		return false;
    	else if(isMetVipsRule8(root))
    		return true;
    	else if(isMetVipsRule9(root))
    		return true;
    	else if(isMetVipsRule11(root))
    		return false;
    	else
    		return false;
	}
    
    private boolean isVisualOther(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    	{
    		isNotValidNode = true;
    		return false;
    	}
    	else if(isMetVipsRule2(root))
    		return false;
    	else if(isMetVipsRule3(root))
			return false;
    	else if(isMetVipsRule4(root))
			return true;
    	else if(isMetVipsRule6(root))
    		return false;
    	else if(isMetVipsRule8(root))
    		return true;
    	else if(isMetVipsRule9(root))
    		return true;
    	else if(isMetVipsRule11(root))
    		return false;
    	else
    		return false;
	}

	private boolean isMetVipsRule1(AreaImpl root)
    {
    	/* If the DOM node is not a valid node and it has no valid children, then this node cannot be divided and will be cut. */
		
		System.out.println("Processing VIPS Rule 1: " + root.toString());
		
    	Box box = root.getBoxes().get(0);
    	boolean noValidChild = true;
    	
    	//if the DOM node is not a valid node
    	if(!box.isVisible())
    	{
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
    	
    	System.out.println("Processing VIPS Rule 2: " + root.toString());
    	
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
    //TODO: check this
    private boolean isMetVipsRule3(AreaImpl root)
    {
    	/*	
    	 	If the DOM node is the root node of the sub-DOM tree (corresponding to the block),
    		and there is only one sub DOM tree corresponding to this block, divide this node.
    	 */
    	
    	System.out.println("Processing VIPS Rule 3: " + root.toString());
    	
    	return false;
    }
    
    private boolean isMetVipsRule4(AreaImpl root)
    {	
    	/* 	
			If all of the child nodes of the DOM node are text nodes or virtual text nodes, do not divide the node.  
			If the font size and font weight of all these child nodes are same, set the DoC of the extracted block to 1. 
			Otherwise, set the DoC of this extracted block to 0.9.
		*/
    	
    	System.out.println("Processing VIPS Rule 4: " + root.toString());
    	
    	float previousNodeWeight = 0;
    	float previousNodeSize = 0;
    	docValue = 0.9f;
    	
    	if(root.getChildCount() == 0)
    		return false;
    	
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
    	System.out.println("Processing VIPS Rule 5: " + root.toString());
    	
    	for (Area child : root.getChildAreas())
    	{
    		if(isLineBreakNode((AreaImpl)child))
    			return true;
		}
    	
    	return false;
    }
    
    private boolean isMetVipsRule6(AreaImpl root)
    {
    	/*	
    	 	If one of the child nodes of the DOM node has HTML tag <HR>, then divide this DOM node.
    	 */
    	System.out.println("Processing VIPS Rule 6: " + root.toString());
    	
    	Box childBox = null;
    	
    	for (Area child : root.getChildAreas())
    	{
    		childBox = child.getBoxes().get(0);
    		if(childBox.getTagName() != null && childBox.getTagName().equals("hr"))
    			return true;
		}
    	
    	return false;
    }
    
    private boolean isMetVipsRule7(AreaImpl root)
    {
    	/*	
    	 	If the background color of this node is different from one of its children’s, divide this node and at the 
			same time, the child node with different background color will not be divided in this round.  
			Set the DoC value (0.6 - 0.8) for the child node based on the html tag of the child node and the size of the 
			child node. 
    	 */
    	System.out.println("Processing VIPS Rule 7: " + root.toString());
    	
    	boolean ruleMet = false;
    	
    	for (Area child : root.getChildAreas())
    	{
    		if(child.getBackgroundColor() != root.getBackgroundColor())
    		{
    			ruleMet = true;
    			nonDividableNodes.add((AreaImpl)child);
    		}
		}
    	
    	if(ruleMet)
    		return true;
    	else
    		return false;
    }
    
    private boolean isMetVipsRule8(AreaImpl root)
    {	
    	/* 	
			If  the  node  has  at  least  one  text  node  child  or  at  least  one  virtual  text  node  child,  and  the  node's  
			relative size is smaller than a threshold, then the node cannot be divided. 
			Set the DoC value (from 0.5 - 0.8) based on the html tag of the node.
		*/
    	System.out.println("Processing VIPS Rule 8: " + root.toString());
    	
    	boolean isVirtual = true;
    	//TODO: Implement DoC eval
    	docValue = 0.5f;
    	docValueIsKnown = true;
    	
    	for (Area child : root.getChildAreas())
    	{
    		//if child node is a text node
			if(child.getBoxes().get(0).getType() == Box.Type.TEXT_CONTENT)
			{
				//TODO: implement threshold evaluation
				//return true;
			}
			//if child node is a virtual text node
			else
			{
				for (Area virtualNodeChild : child.getChildAreas())
				{
					if(virtualNodeChild.getBoxes().get(0).getType() != Box.Type.TEXT_CONTENT)
						isVirtual = false;
				}
				if(isVirtual)
				{
					//TODO: implement threshold evaluation
					//return true;
				}
			}
		}
    	
    	return false;
    }
    
    private boolean isMetVipsRule9(AreaImpl root)
    {	
    	/* 	
			If the child of the node with maximum size is smaller than a threshold (relative size), do not divide this node. 
			Set the DoC based on the html tag and size of this node.
		*/
    	System.out.println("Processing VIPS Rule 9: " + root.toString());
    	
    	int maxI = 0;
    	int maxSize = 0;
    	int i = 0;
    	int size = 0;
    	
    	for (Area child : root.getChildAreas())
    	{
    		size = child.getWidth()*child.getHeight();
    		if(size > maxSize)
    		{
    			maxSize = size;
    			maxI = i;
    		}
    		
    		i++;
		}
    	
    	//TODO:Threshold of root.getChildArea(i)
    	//TODO:Implement DoC evaluation
    	return false;
    }
    
    private boolean isMetVipsRule10(AreaImpl root)
    {	
    	/* 	
			If previous sibling node has not been divided, do not divide this node.
		*/
    	System.out.println("Processing VIPS Rule 10: " + root.toString());
    	
    	if(!visualBlocksPool.contains(root.getPreviousSibling()))
    		return true;
    	else
    		return false;
    }
    
    private boolean isMetVipsRule11(AreaImpl root)
    {	
    	/* 	
			Divide this node.
		*/
    	System.out.println("Processing VIPS Rule 11: " + root.toString());
    	
    	return true;
    }
    
    private boolean isMetVipsRule12(AreaImpl root)
    {	
    	/* 	
			Do not divide this node.
			Set the DoC value based on the html tag and size of this node.
		*/
    	System.out.println("Processing VIPS Rule 12: " + root.toString());
    	
    	//TODO: set Doc
    	
    	return true;
    }
    
    private boolean isInlineNode(AreaImpl root)
    {
    	String tagName = null;
    	if(root.getBoxes().size() != 0)
    		tagName = root.getBoxes().get(0).getTagName();
    	
    	if(tagName == null)
    		return false;
    	
    	//if the node is a inline text element
    	if(tagName!=null && tagName.matches("b|big|i|small|tt|abbr|acronym|cite|code|dfn|em|kbd|strong|samp|time|var|a|bdo|q|span|sub|sup|label"))
    		return true;
    	else
			return false;
    }
    
    private boolean isLineBreakNode(AreaImpl root)
    {
    	return !isInlineNode(root);    	
    }
    
    private void collectSeparators(AreaImpl root)
    {
    	//collecting detected separators at actual tree level
    	collectActualSeparators(root);
    	
    	for (int i = 0; i < root.getChildCount(); i++)
		{
			collectSeparators((AreaImpl) root.getChildArea(i));
		}
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
				removeAreasChildNodes(separator);
				removeIncorectHSeparator(separator);
			}
			else
				detectedSeparators.remove(separator);
		}
    }
    
    private void removeIncorectHSeparator(VipsBasedSeparator separator)
    {
    	int lengthArea1 = separator.getArea1().getX2() - separator.getArea1().getX1();
    	
    	if(separator.getType() == Separator.HORIZONTAL)
    	{
    		if((separator.getArea1().getX1() - separator.getArea2().getX2()) > (lengthArea1))
    			detectedSeparators.remove(separator);
    		else if((separator.getArea2().getX1() - separator.getArea1().getX2()) > (lengthArea1))
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
						divideDomTree.apply(null, root);
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
    		leastDistantChild = null;
    		area1Distance = 0;
    		area2Distance = 0;
    		shortestDistance = 0;
    		area1Reconfigured = false;
    		separatorReconfigured = false;
			
    		for (int i = 0; i < root.getChildCount(); i++)
			{
    			child = root.getChildArea(i);
    			if(!isValidNode((AreaImpl)child))
    				continue;
    			
				if(actualSeparator.getType() == Separator.HORIZONTAL)
				{
					area1Distance = Math.abs(actualSeparator.getY1() - child.getY2());
					area2Distance = Math.abs(actualSeparator.getY2() - child.getY1());
				}
				else if(actualSeparator.getType() == Separator.VERTICAL)
				{
					area1Distance = Math.abs(actualSeparator.getX1() - child.getX2());
					area2Distance = Math.abs(actualSeparator.getX2() - child.getX1());
				}
				
				if(area1Distance < 2)
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
				{
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
    		{
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
    
    /*private List<AreaImpl> getActualNodeVisualBlocks(AreaImpl root, int currentLevel)
    {
    	List<AreaImpl> result = new ArrayList<AreaImpl>();
    	
    	if(dividable(root, currentLevel)) //divide this block
    	{ 
    		if(!isNotValidNode)
    		{
    			if(currentLevel < 1)
    				for (int i = 0; i < root.getChildCount(); i++)
    					result.addAll(getActualNodeVisualBlocks((AreaImpl) root.getChildArea(i), currentLevel++));
    		}
    		else
    			isNotValidNode = false;
    	}
    	else //is a visual block
    	{ 		
			result.add(root);
		}
    	return result;
    }*/
    
    private boolean isValidNode(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    		return false;
    	else {
			return true;
		}
    }
}
