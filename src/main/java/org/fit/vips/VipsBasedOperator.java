/**
 * VipsBasedOperator.java
 */
package org.fit.vips;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fit.layout.impl.BaseOperator;
import org.fit.layout.model.Area;
import org.fit.layout.model.AreaTree;
import org.fit.layout.model.Box;
import org.fit.layout.model.Rectangular;
import org.fit.segm.grouping.AreaImpl;
import org.fit.segm.grouping.op.Separator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Segmentation operator performing a VIPS based algorithm.
 * @author Michal Malanik
 */
public class VipsBasedOperator extends BaseOperator
{
	private static Logger log = LoggerFactory.getLogger(VipsBasedOperator.class);
    
	/** Predefined degree of coherence value */
    protected float pdocValue;
    protected static final int maxPdocValue = 1;
    
    /** Input parameter */
	protected final String[] paramNames = { "pdocValue" };
    protected final ValueType[] paramTypes = { ValueType.FLOAT };
    
    /** Starting tree level of algorithm */
    private static final int startLevel = 0;
    
    /** Actual degree of coherence value */
    private float docValue = 0;
    
    /** Page root */
    private AreaImpl pageRootAreaImpl = null;
    
    /** Page threshold value value */
    private int pageThreshold = 1; //percentage threshold value (1% of pageDimension is default)
    
    /** Print used heuristic rules to output? */
    private boolean printRules = false;
    
    protected List<VipsBasedVisualBlock> visualBlocksPool = new ArrayList<VipsBasedVisualBlock>();
    protected List<VipsBasedSeparator> detectedSeparators = new ArrayList<VipsBasedSeparator>();
    private List<AreaImpl> nonDividableNodes = new ArrayList<AreaImpl>();
    private boolean isNotValidNode = false;
    private boolean docValueIsKnown = false;

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
        return "Operator based on Visual Page Segmentation Algorithm.";
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
    	if((Float.compare(pdocValue, 0) >= 0) && (Float.compare(pdocValue, 1) <= 0))
    		this.pdocValue = pdocValue;
    }
    
    public List<VipsBasedVisualBlock> getVisualBlocksPool()
    {
		return visualBlocksPool;
	}
    
    public List<VipsBasedSeparator> getDetectedSeparators()
    {
		return detectedSeparators;
	}
    
    public int getPageThreshold()
    {
		return pageThreshold;
	}

	public void setPageThreshold(int pageThreshold)
	{
		this.pageThreshold = pageThreshold;
	}

	public AreaImpl getPageRoot()
    {
		return pageRootAreaImpl;
	}

	public void setPageRoot(AreaImpl pageRootAreaImpl)
	{
		this.pageRootAreaImpl = pageRootAreaImpl;
	}
    
    //----------------------------------------------------
    
    @Override
    public void apply(AreaTree atree)
    {
    	setPageRoot((AreaImpl)atree.getRoot());
        performVipsAlgorithm((AreaImpl) atree.getRoot());
    }

    @Override
    public void apply(AreaTree atree, Area root)
    {	
    	setPageRoot((AreaImpl) root);
    	performVipsAlgorithm((AreaImpl) root);
    }
    
    //----------------------------------------------------
    
    /**
     * Performs all phases of segmentation process 
     * @param root node of input AreaTree
     */
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
    
    
    
    /**
     * Joining together all visual blocks on each single line of text
     */
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

    
    
    /**
     * Performs a phase of content structure construction
     * @param root node of input AreaTree
     */
	private void contentStructureConstruction(AreaImpl root)
    {
    	List<AreaImpl> createdSubtrees = new ArrayList<AreaImpl>();
    	List<AreaImpl> rootChilds = new ArrayList<AreaImpl>();
    	Boolean notValidArea = false;
    	
    	for (VipsBasedVisualBlock visualBlock  : visualBlocksPool)
    	{
			createdSubtrees.add(visualBlock.getArea());
		}
    	
    	VipsBasedSeparator actualSeparator = null;
    	AreaImpl newNode = null;
    	while (detectedSeparators.size() != 0)
    	{	
    		actualSeparator = detectedSeparators.get(0);
    		
    		if((actualSeparator.getArea1() != null) && (actualSeparator.getArea2() != null) && (actualSeparator.getArea1() != actualSeparator.getArea2()))
    		{
    			//merge separator's visual blocks to new node
    			newNode = new AreaImpl(0, 0, 0, 0);
        		newNode.appendChild(actualSeparator.getArea1());
        		newNode.appendChild(actualSeparator.getArea2());
        		
        		if(detectedSeparators.size() != 0)
        			detectedSeparators.remove(0);
        		
        		//join all blocks which are siblings and are separated with same weight separator
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
        		updateBounds(newNode);
        		
        		//remove area, which is crossing with another area
        		if(actualSeparator.getType() == Separator.HORIZONTAL)
        		{
        			for (AreaImpl subTree : createdSubtrees)
            		{
    					if(newNode.getBounds().intersects(subTree.getBounds()) && !newNode.getBounds().encloses(subTree.getBounds()))
    					{
    						notValidArea = true;
    						break;
    					}
    				}
        			if(notValidArea)
        			{
        				notValidArea = false;
        				continue;
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
    
	
	
	/**
     * After joining some visual blocks to new one, we need to update bounds of
     * new created area to surround all the children
     * @param newNode area which needs to update bounds
     */
    private void updateBounds(AreaImpl newNode)
    {
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
	}

    
    
    /**
     * Process unused subtrees and appending them to
     * the right place in the content structure
     * @param root node of input AreaTree
     * @param subtrees list of unused subtrees
     * @return list of areas, which are direct children of root
     */
	private List<AreaImpl> processUnusedSubtrees(AreaImpl root, List<AreaImpl> subtrees)
    {
    	Collections.reverse(subtrees);
    	AreaImpl lastIntersectingArea = null;
    	List<AreaImpl> result = new ArrayList<AreaImpl>();
    	
		for (AreaImpl subtree : subtrees)
		{
			lastIntersectingArea = getLastSurroundingArea(root, subtree);
			if(lastIntersectingArea != null)
				lastIntersectingArea.appendChild(subtree);
			else
				result.add(subtree);
		}
		return result;
	}

	
	
	/**
     * Finds the last area in root, which is
     * completely surrounding unused area
     * @param root node of input AreaTree
     * @param unused area
     * @return resulting area
     */
	private AreaImpl getLastSurroundingArea(AreaImpl root, AreaImpl area)
	{
		Area child = null;
		AreaImpl tmpResult = null;
		AreaImpl result = null;
		
		for (int i = 0; i < root.getChildCount(); i++)
		{
			child = root.getChildArea(i);
			if((tmpResult = getLastSurroundingArea((AreaImpl)child, area)) != null)
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

	
	
	/**
     * Performs a phase of content structure construction
     * @param root node of input AreaTree
     * @param currentLevel starting level of input AreaTree
     */
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
    		if(!isNotValidNode)
    			createNewVisualBlock(root);
    		else
    			isNotValidNode = false;
		}
    }
    
	
	
	/**
     * Creates a new visual block
     * @param root node of AreaTree, we want form to visual block
     */
    private void createNewVisualBlock(AreaImpl root)
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
					visualBlock.setDoc(docEvaluation(root, 0.6f, 0.8f));
					visualBlocksPool.add(visualBlock);
					return;
				}
			}
			
			visualBlock.setDoc(docEvaluation(root, 0f, 1f));
		}
		
		visualBlocksPool.add(visualBlock); //add visual block to pool
	}

    
    
    /**
     * Evaluates degree of coherence of visual block
     * @param root input visual block
     * @param min minimum allowed doc
     * @param max maximum allowed doc
     * @result degree of coherence value in given range
     */
	private float docEvaluation(AreaImpl root, float min, float max) {
		float doc = max;
		int frequency = 0;
		int maxFrequency = 0;
		
    	List<AreaImpl> leafNodes = collectLeafNodes(root);
    	
    	List<Float> fontSizes = new ArrayList<Float>();
    	List<Float> fontWeights = new ArrayList<Float>();
    	List<Float> fontStyles = new ArrayList<Float>();
    	List<Float> underLines = new ArrayList<Float>();
    	List<Float> lineThroughs = new ArrayList<Float>();
    	List<Color> backgoundColors = new ArrayList<Color>();
    	
    	float primaryFontSize = 0;
    	float primaryFontWeight = 0;
    	float primaryFontStyle = 0;
    	float primaryUnderLine = 0;
    	float primaryLineThrough = 0;
    	Color primaryColor = null;
    	
    	for (AreaImpl leafNode : leafNodes)
    	{
			fontSizes.add(leafNode.getFontSize());
			fontWeights.add(leafNode.getFontWeight());
			fontStyles.add(leafNode.getFontStyle());
			underLines.add(leafNode.getUnderline());
			lineThroughs.add(leafNode.getLineThrough());
			backgoundColors.add(leafNode.getBackgroundColor());
		}
    	
    	Set<Float> fontSizesSet = new HashSet<Float>(fontSizes);
    	Set<Float> fontWeightsSet = new HashSet<Float>(fontWeights);
    	Set<Float> fontStylesSet = new HashSet<Float>(fontStyles);
    	Set<Float> underLinesSet = new HashSet<Float>(underLines);
    	Set<Float> lineThroughsSet = new HashSet<Float>(lineThroughs);
    	Set<Color> backgoundColorsSet = new HashSet<Color>(backgoundColors);
    	
    	for (Float key : fontSizesSet)
    	{
    		frequency = Collections.frequency(fontSizes, key);
			if(frequency > maxFrequency)
			{
				maxFrequency = frequency;
				primaryFontSize = key;
			}
		}
    	maxFrequency = 0;
    	for (Float key : fontWeightsSet)
    	{
    		frequency = Collections.frequency(fontWeights, key);
			if(frequency > maxFrequency)
			{
				maxFrequency = frequency;
				primaryFontWeight = key;
			}
		}
    	maxFrequency = 0;
    	for (Float key : fontStylesSet)
    	{
    		frequency = Collections.frequency(fontStyles, key);
			if(frequency > maxFrequency)
			{
				maxFrequency = frequency;
				primaryFontStyle = key;
			}
		}
    	maxFrequency = 0;
    	for (Float key : underLinesSet)
    	{
    		frequency = Collections.frequency(underLines, key);
			if(frequency > maxFrequency)
			{
				maxFrequency = frequency;
				primaryUnderLine = key;
			}
		}
    	maxFrequency = 0;
    	for (Float key : lineThroughsSet)
    	{
    		frequency = Collections.frequency(lineThroughs, key);
			if(frequency > maxFrequency)
			{
				maxFrequency = frequency;
				primaryLineThrough = key;
			}
		}
    	maxFrequency = 0;
    	for (Color key : backgoundColorsSet)
    	{
    		frequency = Collections.frequency(backgoundColors, key);
			if(frequency > maxFrequency)
			{
				maxFrequency = frequency;
				primaryColor = key;
			}
		}
    	
    	for (AreaImpl leafNode : leafNodes)
    	{
			if(Float.compare(primaryFontSize, leafNode.getFontSize()) != 0)
				doc -= 0.1f;
			if(Float.compare(primaryFontWeight, leafNode.getFontWeight()) != 0)
				doc -= 0.1f;
			if(Float.compare(primaryFontStyle, leafNode.getFontStyle()) != 0)
				doc -= 0.1f;
			if(Float.compare(primaryUnderLine, leafNode.getUnderline()) != 0)
				doc -= 0.1f;
			if(Float.compare(primaryLineThrough, leafNode.getLineThrough()) != 0)
				doc -= 0.1f;
			if((leafNode.getBackgroundColor()) != null && (primaryColor != null))
			{
				if(!primaryColor.equals(leafNode.getBackgroundColor()))
					doc -= 0.25f;
			}
			else if((leafNode.getBackgroundColor() == null && primaryColor != null) || (leafNode.getBackgroundColor() != null && primaryColor == null))
				doc -= 0.25f;
			
			if(Float.compare(doc, min) <= 0)
				return min;
		}
    	
		return doc;
	}

	
	
	private List<AreaImpl> collectLeafNodes(AreaImpl root)
	{
		List<AreaImpl> result = new ArrayList<AreaImpl>();
		
		if(root.getChildCount() == 0)
			result.add(root);
		else
		{
			for (Area child : root.getChildAreas()) 
				result.addAll(collectLeafNodes((AreaImpl)child));
		}
		
		return result;
	}

	
	
	/**
     * Is current node dividable?
     * @param root input node of AreaTree
     * @param currentLevel node's current level in AreaTree
     * @result true if node is dividable, otherwise false
     */
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
    
	
	
	/**
     * Checks, if current node forms a visual block based on heuristic rules
     * @param root input node of AreaTree
     * @result true if node is visual block, otherwise false
     */
    private boolean isVisualBlock(AreaImpl root)
    {
    	String tagName = null;
    	if(root.getBoxes().size() != 0)
    		tagName = root.getBoxes().get(0).getTagName();
    	
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
    	else if(isMetImprovedVipsRule3(root))
    	{
    		if(printRules)
    			System.out.println("IMPROVED VIPS RULE 3 MATCH!");
    		isNotValidNode = true;
    		return false;
    	}
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

     //////////////////////////////////////////
     // SECTION OF HEURISTIC RULES
     //////////////////////////////////////////
    
    
    
	private boolean isMetVipsRule1(AreaImpl root)
    {
    	/* If the DOM node is not a valid node and it has no valid children, then this node cannot be divided and will be cut. */
		
		if(printRules)
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

		if(printRules)
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
    
    
    
    //removed rule
    private boolean isMetVipsRule3(AreaImpl root)
    {
    	/*	
    	 	If the DOM node is the root node of the sub-DOM tree (corresponding to the block),
    		and there is only one sub DOM tree corresponding to this block, divide this node.
    	 */

		if(printRules)
			System.out.println("Processing VIPS Rule 3: " + root.toString());
    	
    	return false;
    }
    
    
    
    private boolean isMetVipsRule4(AreaImpl root)
    {	
    	/* 	
			If all of the child nodes of the DOM node are text nodes or virtual text nodes, do not divide the node.  
			If the font size and font weight of all these child nodes are same, set the DoC of the extracted block to 1.
			Else if only the font size isn't same for all children proceed improvedVipsRule1 or improvedVipsRule2.
			Else if only the font weight isn't same for all children set DoC of the extracted block to 0.9.
		*/

		if(printRules)
			System.out.println("Processing VIPS Rule 4: " + root.toString());
    	
    	float previousNodeWeight = 0;
    	float previousNodeSize = 0;
    	boolean proceedImprovedVipsRules = false;
    	docValue = 1f;
    	
    	if(root.getChildCount() == 0)
    		return false;
    	
    	for (Area child : root.getChildAreas())
    	{
			if(!isTextNode(child) && !isVirtualTextNode(child)) //if child node isn't a text node even a virtual text node
				return false;

			//font size and font weight comparison
			if(child == root.getChildAreas().get(0))
			{
				previousNodeWeight = child.getFontWeight();
				previousNodeSize = child.getFontSize();
			}
			else
			{
				if(Float.compare(previousNodeSize, child.getFontSize()) != 0)
				{
					proceedImprovedVipsRules = true;
					docValue = 0.9f;
				}
				if(Float.compare(previousNodeWeight, child.getFontWeight()) != 0)
				{
					docValue = 0.9f;
				}
			}
		}
    	
    	if(proceedImprovedVipsRules)
    	{
    		if(isMetImprovedVipsRule1(root))
    		{
        		isNotValidNode = true;
        		return true;
        	}
    		else if(isMetImprovedVipsRule2(root))
    		{
        		isNotValidNode = true;
        		return true;
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
		if(printRules)
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
    	 	Original VIPS rule:
    	 	If one of the child nodes of the DOM node has HTML tag <HR>, then divide this DOM node.
    	 	Improved VIPS rule:
    	 	If a node contains a child whose tag is HR, BR or any of line-break nodes which
			has no valid children, then divide this DOM node.
    	 */
		if(printRules)
			System.out.println("Processing VIPS Rule 6: " + root.toString());
    	
    	Box childBox = null;
    	
    	for (Area child : root.getChildAreas())
    	{
    		childBox = child.getBoxes().get(0);
    		if(childBox.getTagName() != null)
    		{
    			if(childBox.getTagName().equals("hr"))
        			return true;
        		else if(childBox.getTagName().equals("br"))
        			return true;
        		else if(isLineBreakNode((AreaImpl)child))
        		{
        			if(child.getChildCount() == 0)
        				return true;
        			else
        			{
						for (Area grandChild : child.getChildAreas())
						{
							//if grandChild is valid
							if(!isMetVipsRule1((AreaImpl)grandChild))
								return false;
						}
						return true;
					}
        		}
    		}
		}
    	return false;
    }
    
    
    
    private boolean isMetVipsRule7(AreaImpl root)
    {
    	/*	
    	 	If the background color of this node is different from one of its children’s, divide this node and at the 
			same time, the child node with different background color will not be divided in this round.  
			Set the DoC value (0.6 - 0.8) for the child node. 
    	 */
		if(printRules)
			System.out.println("Processing VIPS Rule 7: " + root.toString());
    	
    	boolean ruleMet = false;
    	
    	for (Area child : root.getChildAreas())
    	{
    		if(child.getBackgroundColor() != null && root.getBackgroundColor() != null)
    		{
    			if(!child.getBackgroundColor().equals(root.getBackgroundColor()))
	    		{
	    			ruleMet = true;
	    			nonDividableNodes.add((AreaImpl)child);
	    		}
    		}
    		else if((child.getBackgroundColor() == null && root.getBackgroundColor() != null) || (child.getBackgroundColor() != null && root.getBackgroundColor() == null))
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
			Set the DoC value from 0.5 to 0.8.
		*/
		if(printRules)
			System.out.println("Processing VIPS Rule 8: " + root.toString());
    	
    	for (Area child : root.getChildAreas())
    	{
    		//if child node is a text node or virtual text node
			if(isTextNode(child) || isVirtualTextNode(child))
			{
				if(isSmallerThanThreshold(root))
				{
					docValue = docEvaluation(root, 0.8f, 0.5f);
			    	docValueIsKnown = true;
					return true;
				}
			}
		}
    	return false;
    }

    
    
	private boolean isMetVipsRule9(AreaImpl root)
    {	
    	/* 	
			If the child of the node with maximum size is smaller than a threshold (relative size), do not divide this node.
		*/
		if(printRules)
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
    	
    	if(root.getChildCount() != 0 && isSmallerThanThreshold((AreaImpl)root.getChildArea(maxI)))
    		return true;
    	else
    		return false;
    }
    
	
	
    private boolean isMetVipsRule10(AreaImpl root)
    {	
    	/* 	
			If previous sibling node has not been divided, do not divide this node.
		*/
		if(printRules)
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
		if(printRules)
			System.out.println("Processing VIPS Rule 11: " + root.toString());
    	
    	return true;
    }
    
    
    
    private boolean isMetVipsRule12(AreaImpl root)
    {	
    	/* 	
			Do not divide this node.
		*/
		if(printRules)
			System.out.println("Processing VIPS Rule 12: " + root.toString());

    	return true;
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
				if(child.getFontSize() > child.getPreviousSibling().getFontSize())
				{
					AreaImpl newNode1 = new AreaImpl(0,0,0,0);
					AreaImpl newNode2 = new AreaImpl(0,0,0,0);
					newNode1.addBox(root.getBoxes().get(0));
					newNode2.addBox(root.getBoxes().get(0));
					List<Area> selected1 = new ArrayList<Area>();
					List<Area> selected2 = new ArrayList<Area>();
					
					for (int j = 0; j < root.getChildCount(); j++)
					{
						if(j < i)
							selected1.add(root.getChildArea(j));
						else
							selected2.add(root.getChildArea(j));
					}
					
					for (Area subArea : selected1)
					{
						newNode1.appendChild(subArea);
					}
					for (Area subArea : selected2)
					{
						newNode2.appendChild(subArea);
					}
					
					updateBounds(newNode1);
					updateBounds(newNode2);
					
					root.appendChild(newNode1);
					root.appendChild(newNode2);
					
					collectActualSeparators(root);
					
					createNewVisualBlock(newNode1);
					createNewVisualBlock(newNode2);
					reconfigureSeparators(root);
					
					return true;
				}
			}
		}
    	return false;
	}
    
    
    
    private boolean isMetImprovedVipsRule2(AreaImpl root)
    {
		/*
		  	If the first child of the node has bigger font size than the remaining children,
		  	divide node into two blocks, one in which is the first child with bigger font size, and the other
			contains remaining children.
		 */
    	
    	Area firstChild = null;
    	Area child = null;
    	Boolean ruleIsMet = true;
    	List<Area> selected = new ArrayList<Area>();
    	
    	if(root.getChildCount() <= 1)
    		return false;
    	
    	for (int i = 0; i < root.getChildCount(); i++)
    	{
    		child = root.getChildArea(i);
    		
    		if(i == 0)
    			firstChild = child;
    		else
    		{
    			if(firstChild.getFontSize() <= child.getFontSize())
    				ruleIsMet = false;
    			
    			selected.add(child);
			}
    	}
    	
    	if(ruleIsMet)
    	{
    		AreaImpl newNode = new AreaImpl(0,0,0,0);
    		newNode.addBox(root.getBoxes().get(0));
    		
    		for (Area subArea : selected)
			{
				newNode.appendChild(subArea);
			}
    		
    		updateBounds(newNode);
    		
    		root.appendChild(newNode);
    		collectActualSeparators(root);
    		
    		createNewVisualBlock((AreaImpl)firstChild);
    		createNewVisualBlock(newNode);
    		
    		reconfigureSeparators(root);

    		return true;
    	}
    	return false;
	}
    
    
    
    private boolean isMetImprovedVipsRule3(AreaImpl root)
    {
    	/*
		  	If node is a table and some of its columns have different background color than the
			others, divide the table into the number separate columns and construct a visual block for each piece.
		 */
		AreaImpl row = null;
		AreaImpl firstRow = null;
		AreaImpl cell = null;
		AreaImpl newNode = null;
		Boolean differentColumns = false;
		Boolean divideTable = false;
		Boolean firstIsHead = false;
		List<AreaImpl> rows = new ArrayList<AreaImpl>();
		List<Color> completedColumns = new ArrayList<Color>();
		Color firstColor = null;
		
		if(root.getChildArea(0) != null && root.getChildArea(0).getBoxes().get(0) != null && root.getChildArea(0).getBoxes().get(0).getTagName() != null)
		{
			if(root.getChildArea(0).getBoxes().get(0).getTagName().equals("thead") && root.getChildArea(0).getChildArea(0) != null)
			{
				firstRow = (AreaImpl)root.getChildArea(0).getChildArea(0);
				firstIsHead = true;
			}
			else if(root.getChildArea(0).getBoxes().get(0).getTagName().equals("thead") && root.getChildArea(0).getChildArea(0) != null)
			{
				firstRow = (AreaImpl)root.getChildArea(0).getChildArea(0);
			}
			else
				return false;
		}
		
		for (int i = 0; i < firstRow.getChildCount(); i++)
		{
			cell = (AreaImpl)firstRow.getChildArea(i);
			
			if(cell.getNextSibling() != null)
				if(!cell.hasSameBackground((AreaImpl)cell.getNextSibling()))
					differentColumns = true;
		}
			
		if(differentColumns)
		{		
			for (Area tr : root.getChildArea(0).getChildAreas())
			{
				rows.add((AreaImpl)tr);
			}
			if(firstIsHead)
				if(root.getChildArea(1) != null)
					for (Area tr : root.getChildArea(1).getChildAreas())
					{
						rows.add((AreaImpl)tr);
					}
			
			for (int i = 0; i < firstRow.getChildCount(); i++)
			{
				for (int j = 0; j < rows.size(); j++)
				{
					row = rows.get(j);
					cell = (AreaImpl)row.getChildArea(i);
					
					if(row.getNextSibling() != null && row.getNextSibling().getChildArea(i) != null)
					{
						if(!cell.hasSameBackground((AreaImpl)row.getNextSibling().getChildArea(i)))
							break;
					}
					else
					{
						completedColumns.add(cell.getBackgroundColor());
						break;
					}
				}
			}
			
			for (Color color : completedColumns)
			{
				if(color == completedColumns.get(0))
					firstColor = color;
				else
				{
					if(color != null && firstColor != null)
					{
						if(!color.equals(firstColor))
						{
							divideTable = true;
							break;
						}
					}
					else if((color == null && firstColor != null) || (color != null && firstColor == null))
					{
						divideTable = true;
						break;
					}
				}
			}
			if(divideTable)
			{
				List<AreaImpl> column = null;
				List<List<AreaImpl>> columns = new ArrayList<List<AreaImpl>>();
				for (int i = 0; i < firstRow.getChildCount(); i++)
				{
					column = new ArrayList<AreaImpl>();
					for (int j = 0; j < rows.size(); j++)
					{
						row = rows.get(j);
						column.add((AreaImpl)row.getChildArea(i));
					}
					columns.add(column);
				}
				
				List<AreaImpl> newNodes = new ArrayList<AreaImpl>();
				for (List<AreaImpl> col : columns)
				{
						newNode = new AreaImpl(0,0,0,0);
			    		newNode.addBox(root.getBoxes().get(0));
			    		
			    		for (AreaImpl subArea : col)
						{
							newNode.appendChild(subArea);
						}
			    		
			    		updateBounds(newNode);
			    		
			    		newNodes.add(newNode);
			    		collectActualSeparators(newNode);
				}
				
				root.removeAllChildren();
				for (AreaImpl col : newNodes)
				{
					root.appendChild(col);
					createNewVisualBlock(col);
				}
				
	    		collectActualSeparators(root);
	    		reconfigureSeparators(root);

	    		return true;
			}
		}	
		return false;
	}
    
	//////////////////////////////////////////
	// END OF HEURISTIC RULES SECTION
	//////////////////////////////////////////
    
    
    
    /**
     * Check, if current node is smaller than threshold
     * @param root current node
     * @result true if current node is smaller than threshold, false otherwise
     */
    private boolean isSmallerThanThreshold(AreaImpl root)
    {
    	double pageDimension = getPageRoot().getWidth() * getPageRoot().getHeight();
    	double nodeDimension = root.getWidth() * root.getHeight();
    	double threshold = pageDimension * (this.getPageThreshold()/100);
    	
    	//if node dimension is smaller than threshold
    	if(Double.compare(nodeDimension, threshold) <= 0)
    		return true;
    	else    	
    		return false;
	}
    
    
    
    /**
     * Check, if current node is inline node
     * @param root current node
     * @result true if current node is inline node, false otherwise
     */
    private boolean isInlineNode(AreaImpl root)
    {
    	String tagName = null;
    	if(root.getBoxes().size() != 0)
    		tagName = root.getBoxes().get(0).getTagName();
    	
    	if(tagName == null)
    		return false;
    	
    	//if the node is a inline text element
    	if(tagName!=null && tagName.matches("b|big|i|small|tt|abbr|acronym|cite|code|dfn|em|kbd|strong|samp|time|var|a|bdo|q|span|sub|sup|label|u|s|strike|del|ins|mark|ruby"))
    		return true;
    	else
			return false;
    }
    
    
    
    /**
     * Check, if current node is a text node
     * @param root current node
     * @result true if current node is a text node, false otherwise
     */
    private boolean isTextNode(Area node)
	{
    	Box box = node.getBoxes().get(0);
		if(box.getType() == Box.Type.TEXT_CONTENT)
		{
			if((node.getText().trim().isEmpty()) || (Float.compare(node.getFontSize(), 0.0f) == 0))
				return false;
			else
				return true;
		}
		else
			return false;
	}

    
    
    /**
     * Check, if current node is a virtual text node
     * @param root current node
     * @result true if current node is a virtual text node, false otherwise
     */
	private boolean isVirtualTextNode(Area node)
	{
		if(node.getChildCount() > 0)
		{
			for (Area child : node.getChildAreas())
			{	
				//if child is not a text node
				if(!isTextNode(child))
				{
					if(child.getChildCount() > 0)
					{
						//all child nodes of child must be text nodes
						for (Area grandChild : child.getChildAreas())
						{
							//if grandChild is not text node
							if(!isTextNode(grandChild))
							{
								return false;
							}
						}
					}
					else
						return false;
				}
			}
		}
		else
			return false;
		
		return true;
	}
    
	
	
	/**
     * Check, if current node is a line break node
     * @param root current node
     * @result true if current node is a line break node, false otherwise
     */
    private boolean isLineBreakNode(AreaImpl root)
    {
    	return !isInlineNode(root);    	
    }
    
    
    
    /**
     * Phase of visual separators detection
     * @param root node of input AreaTree
     */
    private void collectSeparators(AreaImpl root)
    {
    	//collecting detected separators at actual tree level
    	collectActualSeparators(root);
    	
    	for (int i = 0; i < root.getChildCount(); i++)
		{
			collectSeparators((AreaImpl) root.getChildArea(i));
		}
    }
    
    
    
    /**
     * Collects separators on actual level of tree
     * @param root node of input AreaTree
     */
    private void collectActualSeparators(AreaImpl root)
    {
    	VipsBasedSeparatorSet actualLevelSeparators = new VipsBasedSeparatorSet(root);
    	VipsBasedSeparator vipsSeparator = null;
    	
    	for (Separator separator : actualLevelSeparators.getHorizontal())
    	{
    		//System.out.println("Horizontal separator");
    		vipsSeparator = new VipsBasedSeparator(separator);
    		//if(!detectedSeparators.contains(vipsSeparator))
    			detectedSeparators.add(vipsSeparator);
		}
    	for (Separator separator : actualLevelSeparators.getVertical())
    	{
    		//System.out.println("Vertical separator");
    		vipsSeparator = new VipsBasedSeparator(separator);
    		//if(!detectedSeparators.contains(vipsSeparator))
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
    
    
    
    /**
     * Removes incorrect HSeparators
     * @param separator checked detected separator
     */
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

    
    
    /**
     * Removing useless child nodes from detected separators surrounding areas
     * @param root node if input AreaTree
     */
	private void removeAreasChildNodes(VipsBasedSeparator separator)
    {
    	AreaImpl area1 = separator.getArea1();
    	AreaImpl area2 = separator.getArea2();
    	area1.removeAllChildren();
    	area2.removeAllChildren();
    	separator.setArea1(area1);
    	separator.setArea2(area2);
    }
    
	
	
	/**
     * Granularity condition check - 
     * finds a leaf nodes and on every leaf node checks
     * the granularity condition
     * @param root node if input AreaTree
     */
    private AreaImpl processLeafNodes(AreaImpl root)
    {
    	if(root.getChildCount() != 0)
    	{
    		//Non-leaf node
    		for (int i = 0; i < root.getChildCount(); i++)
    			processLeafNodes((AreaImpl) root.getChildArea(i));
    	}
    	else
    	{
    		//Leaf node
			for (VipsBasedVisualBlock visualBlock : visualBlocksPool)
			{
				if(root == visualBlock.getArea())
				{				
					if(Float.compare(visualBlock.getDoc(), pdocValue) <= 0)
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
    
    
    
    /**
     * Reconfiguring of detected separators
     * @param root node if input AreaTree
     */
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
    
    
    
    /**
     * Check, if current node is a valid node
     * @param root current node
     * @result true if current node is a valid node, false otherwise
     */
    private boolean isValidNode(AreaImpl root)
    {
    	if(isMetVipsRule1(root))
    		return false;
    	else {
			return true;
		}
    }
}
