package de.ipbhalle.metfraglib.fragment;

import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesGenerator;

import de.ipbhalle.metfraglib.FastBitArray;
import de.ipbhalle.metfraglib.exceptions.AtomTypeNotKnownFromInputListException;
import de.ipbhalle.metfraglib.interfaces.IFragment;
import de.ipbhalle.metfraglib.interfaces.IMatch;
import de.ipbhalle.metfraglib.interfaces.IMolecularFormula;
import de.ipbhalle.metfraglib.interfaces.IMolecularStructure;
import de.ipbhalle.metfraglib.interfaces.IPeak;
import de.ipbhalle.metfraglib.match.FragmentMassToPeakMatch;
import de.ipbhalle.metfraglib.molecularformula.BitArrayFragmentMolecularFormula;
import de.ipbhalle.metfraglib.parameter.Constants;
import de.ipbhalle.metfraglib.peak.TandemMassPeak;
import de.ipbhalle.metfraglib.precursor.BitArrayPrecursor;

/**
 * FastBitArrayFragment is an memory efficient way to store a fragment.
 * It is always related to a CDK AtomContainer object.
 * 
 * @author c-ruttkies
 *
 */
public class DefaultBitArrayFragment extends AbstractFragment {

	protected IMolecularFormula molecularFormula;
	protected short numberHydrogens;

	/**
	 * atoms represented as FastBitArray object 
	 * 
	 */
	protected de.ipbhalle.metfraglib.FastBitArray atomsFastBitArray;
	protected de.ipbhalle.metfraglib.FastBitArray bondsFastBitArray;
	protected de.ipbhalle.metfraglib.FastBitArray brokenBondsFastBitArray;
	
	/**
	 * constructor setting precursor molecule of fragment
	 * 
	 * @param precursor
	 */
	public DefaultBitArrayFragment(IMolecularStructure precursorMolecule) {
		super(precursorMolecule);
		this.atomsFastBitArray = new FastBitArray(precursorMolecule.getNonHydrogenAtomCount(), true);
		this.bondsFastBitArray = new FastBitArray(precursorMolecule.getNonHydrogenBondCount(), true);
		this.brokenBondsFastBitArray = new FastBitArray(precursorMolecule.getNonHydrogenBondCount());
		this.treeDepth = 0;
	}
	
	/**
	 * 
	 * @param atomsFastBitArray
	 * @param bondsFastBitArray
	 */
	public DefaultBitArrayFragment(BitArrayPrecursor precursorMolecule, de.ipbhalle.metfraglib.FastBitArray atomsFastBitArray, de.ipbhalle.metfraglib.FastBitArray bondsFastBitArray, de.ipbhalle.metfraglib.FastBitArray brokenBondsFastBitArray) {
		super(precursorMolecule);
		this.atomsFastBitArray = atomsFastBitArray;
		this.bondsFastBitArray = bondsFastBitArray;
		this.brokenBondsFastBitArray = brokenBondsFastBitArray;
		this.treeDepth = 0;
	}
	
	public DefaultBitArrayFragment(BitArrayPrecursor precursorMolecule, de.ipbhalle.metfraglib.FastBitArray atomsFastBitArray) {
		super(precursorMolecule);
		this.atomsFastBitArray = atomsFastBitArray;
		this.bondsFastBitArray = new FastBitArray(precursorMolecule.getNonHydrogenBondCount(), false);
		this.brokenBondsFastBitArray = new FastBitArray(precursorMolecule.getNonHydrogenBondCount(), false);
		for(int i = 0; i < this.atomsFastBitArray.getSize(); i++) {
			short[] atomIndeces = precursorMolecule.getConnectedAtomIndecesOfAtomIndex((short)i);
			if(this.atomsFastBitArray.get(i)) {
				for(int k = 0; k < atomIndeces.length; k++) {
					if(this.atomsFastBitArray.get(atomIndeces[k])) 
						this.bondsFastBitArray.set(precursorMolecule.getBondIndexFromAtomAdjacencyList((short)i, atomIndeces[k]) - 1);
					else 
						this.brokenBondsFastBitArray.set(precursorMolecule.getBondIndexFromAtomAdjacencyList((short)i, atomIndeces[k]) - 1);
				}
			}
			else {
				for(int k = 0; k < atomIndeces.length; k++) {
					if(this.atomsFastBitArray.get(atomIndeces[k])) 
						this.brokenBondsFastBitArray.set(precursorMolecule.getBondIndexFromAtomAdjacencyList((short)i, atomIndeces[k]) - 1);
				}
			}
		}
		
		this.treeDepth = 0;
	}

	protected void initialiseNumberHydrogens() {
		this.numberHydrogens = 0;
		for(int i = 0; i < this.atomsFastBitArray.getSize(); i++) {
			if(this.atomsFastBitArray.get(i)) {
				this.numberHydrogens += this.precursorMolecule.getNumberHydrogensConnectedToAtomIndex(i);
			}
		}
	}
	
	public double getMonoisotopicMass() {
		//return this.molecularFormula.getMonoisotopicMass();
		double mass = 0.0;
		for(int i = 0; i < this.atomsFastBitArray.getSize(); i++) {
			if(this.atomsFastBitArray.get(i)) {
				mass += this.precursorMolecule.getMassOfAtom(i);
			}
		}
		return mass;
	}
	
	public void initialiseMolecularFormula() throws AtomTypeNotKnownFromInputListException {
		this.molecularFormula = new BitArrayFragmentMolecularFormula((BitArrayPrecursor)this.precursorMolecule, this.atomsFastBitArray);
	}
	
	@Override
	public byte matchToPeak(IPeak peak, int precursorIonTypeIndex, boolean isPositive, IMatch[] fragmentPeakMatch) {
		if(fragmentPeakMatch == null || fragmentPeakMatch.length != 1) return -1;
		double[] ionisationTypeMassCorrection = new double [] {
			Constants.getIonisationTypeMassCorrection(precursorIonTypeIndex, isPositive),
			Constants.getIonisationTypeMassCorrection(0, isPositive)
		};
		byte[] signs = {1, -1};
		byte[] compareResultValuesToChangeHydrogenCheck = {1, -1};
		byte numberCompareResultsEqualPlusOne = 0;
		byte numberComparisons = 2;
		boolean matched = false;
		
		short numberHydrogens = this.getNumberHydrogens();
		
		for(int i = 0; i < ionisationTypeMassCorrection.length; i++) {
			int substractHydrogenFromCharge = 0;
			if(i == 0 && precursorIonTypeIndex == 1) substractHydrogenFromCharge = 1;
			boolean[] toCheckHydrogenShiftType = {true, true};
			double currentFragmentMass = this.getMonoisotopicMass() + ionisationTypeMassCorrection[i];
			byte compareResult = ((TandemMassPeak)peak).matchesToMass(currentFragmentMass);
			if(compareResult == 0 && substractHydrogenFromCharge <= numberHydrogens) {
				/*
				 * if a former fragment has matched already then add the current fragment list to the match object
				 */
				if(fragmentPeakMatch[0] != null) {
					((FragmentMassToPeakMatch)fragmentPeakMatch[0]).addMatchedFragment(this, (byte)0, currentFragmentMass, i == 0 ? (byte)precursorIonTypeIndex : (byte)0);
				}
				/*
				 * if no former fragment matched before initialise the match object and add the fragment list
				 */
				else {
					fragmentPeakMatch[0] = new FragmentMassToPeakMatch(peak);
					fragmentPeakMatch[0].setIsPositiveCharge(isPositive);
					((FragmentMassToPeakMatch)fragmentPeakMatch[0]).addMatchedFragment(this, (byte)0, currentFragmentMass, i == 0 ? (byte)precursorIonTypeIndex : (byte)0);
				}
				matched = true;
			}
			else if(compareResultValuesToChangeHydrogenCheck[0] == compareResult) 
				toCheckHydrogenShiftType[0] = false;
			else if(compareResultValuesToChangeHydrogenCheck[1] == compareResult) 
				toCheckHydrogenShiftType[1] = false;
			if(compareResult == 1) numberCompareResultsEqualPlusOne++;
			/*
			 * iteration of hydrogenShift numbers to calculate adapted fragment masses
			 */
			int maximalHydrogenShift = this.getTreeDepth();
			for(byte hydrogenShift = 1; 
				hydrogenShift <= maximalHydrogenShift; 
				hydrogenShift++) 
			{
				/*
				 * check all signs to model hydrogen loss (-1) and hydrogen addition (+1)
				 */
				for(byte signIndex = 0; signIndex < signs.length; signIndex++) {
					if(!toCheckHydrogenShiftType[signIndex]) {
						continue;
					}
					if(numberHydrogens - (signs[signIndex] * hydrogenShift - substractHydrogenFromCharge) < 0) {
						continue;
					}
					/*
					 * calculate and check adapted fragment mass 
					 */
					double currentFragmentMassIonModeCorrected = currentFragmentMass + signs[signIndex] * hydrogenShift * Constants.HYDROGEN_MASS;
					compareResult = ((TandemMassPeak)peak).matchesToMass(currentFragmentMassIonModeCorrected);
					numberComparisons++;
					/*
					 * in case fragment matches to peak create match or add the fragment to the match
					 * if result is 0 then the fragment mass matched to the peak
					 */
					if(compareResult == 0) {
						/*
						 * if a former fragment has matched already then add the current fragment list to the match object
						 */
						if(fragmentPeakMatch[0] != null) {
							((FragmentMassToPeakMatch)fragmentPeakMatch[0]).addMatchedFragment(this, (byte)(signs[signIndex] * hydrogenShift), currentFragmentMassIonModeCorrected, i == 0 ? (byte)precursorIonTypeIndex : (byte)0);
						}
						/*
						 * if no former fragment matched before initialise the match object and add the fragment list
						 */
						else {
							fragmentPeakMatch[0] = new FragmentMassToPeakMatch(peak);
							fragmentPeakMatch[0].setIsPositiveCharge(isPositive);
							((FragmentMassToPeakMatch)fragmentPeakMatch[0]).addMatchedFragment(this, (byte)(signs[signIndex] * hydrogenShift), currentFragmentMassIonModeCorrected, i == 0 ? (byte)precursorIonTypeIndex : (byte)0);
						}
						matched = true;
					}
					/*
					 * if hydrogen removed/added from fragment mass and fragment mass smaller/greater than peak mass 
					 * there is no need to check further by removing/adding hydrogens
					 */
					else if(compareResultValuesToChangeHydrogenCheck[signIndex] == compareResult) 
						toCheckHydrogenShiftType[signIndex] = false;
					if(compareResult == 1) numberCompareResultsEqualPlusOne++;
				}
			}
		}
		if(matched == true) {
			this.hasMatched = true;
			return 0;
		}
		else if(numberCompareResultsEqualPlusOne == numberComparisons) return 1;
		return -1;
	}	

	/**
	 * 
	 * @param atomIndex
	 * @return
	 */
	public boolean getAtomBit(int atomIndex) {
		return this.atomsFastBitArray.get(atomIndex);
	}
	
	/**
	 * 
	 * @param atomIndex
	 * @param value
	 */
	public void setAtomBit(int atomIndex, boolean value) {
		this.atomsFastBitArray.set(atomIndex, value);
	}

	public de.ipbhalle.metfraglib.interfaces.IMolecularStructure getPrecursor() {
		return this.precursorMolecule;
	}

	public de.ipbhalle.metfraglib.FastBitArray getAtomsFastBitArray() {
		return this.atomsFastBitArray;
	}

	public void setAtomsFastBitArray(de.ipbhalle.metfraglib.FastBitArray atomsFastBitArray) {
		this.atomsFastBitArray = atomsFastBitArray;
	}

	public de.ipbhalle.metfraglib.FastBitArray getBondsFastBitArray() {
		return this.bondsFastBitArray;
	}

	public de.ipbhalle.metfraglib.FastBitArray getBrokenBondsFastBitArray() {
		return this.brokenBondsFastBitArray;
	}
	
	public void setBondsFastBitArray(de.ipbhalle.metfraglib.FastBitArray bondsFastBitArray) {
		this.bondsFastBitArray = bondsFastBitArray;
	}

	public void setBrokenBondsFastBitArray(de.ipbhalle.metfraglib.FastBitArray brokenBondsFastBitArray) {
		this.brokenBondsFastBitArray = brokenBondsFastBitArray;
	}

	public IAtomContainer getPrecursorAsIAtomContainer() {
		return this.precursorMolecule.getStructureAsIAtomContainer();
	}
	
	public boolean equals(IFragment fragment) {
		DefaultBitArrayFragment curFragment = (DefaultBitArrayFragment)fragment;
		return this.atomsFastBitArray.equals(curFragment.getAtomsFastBitArray());
	}
	
	public void setNumberHydrogens(int numberHydrogens) {
		this.numberHydrogens = (short)numberHydrogens;
	}
	
	public short getNumberHydrogens() {
		return this.numberHydrogens;
	}
	
	public IMolecularFormula getMolecularFormula() {
		try {
			BitArrayFragmentMolecularFormula form = new BitArrayFragmentMolecularFormula((BitArrayPrecursor)this.precursorMolecule, this.atomsFastBitArray);
			return form;
		} catch (AtomTypeNotKnownFromInputListException e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getSmiles() {
		IAtomContainer molecule = this.getStructureAsIAtomContainer();
	
		SmilesGenerator sg = new SmilesGenerator();
		String smiles = null;
		try {
			smiles = sg.create(molecule);
		} catch (CDKException e) {
			e.printStackTrace();
		}
		return smiles;
	}

	public String getAromaticSmiles() {
		IAtomContainer molecule = this.getStructureAsAromaticIAtomContainer();
		SmilesGenerator sg = SmilesGenerator.generic().aromatic();
		String smiles = null;
		try {
			smiles = sg.create(molecule);
		} catch (CDKException e) {
			e.printStackTrace();
		}
		return smiles;
	}
	
	public IMolecularStructure getPrecursorMolecule() {
		return this.precursorMolecule;
	}

	public IAtomContainer getStructureAsAromaticIAtomContainer() {
		IChemObjectBuilder builder = DefaultChemObjectBuilder.getInstance();
		IAtomContainer fragmentStructure = builder.newInstance(IAtomContainer.class);
		if(this.atomsFastBitArray.cardinality() == 1) {
			fragmentStructure.addAtom(this.precursorMolecule.getStructureAsIAtomContainer().getAtom(this.atomsFastBitArray.getFirstSetBit()));
			if(this.precursorMolecule.getStructureAsIAtomContainer().getAtom(this.atomsFastBitArray.getFirstSetBit()).isAromatic())
				fragmentStructure.getAtom(0).setIsAromatic(true);
			else 
				fragmentStructure.getAtom(0).setIsAromatic(false);
			return fragmentStructure;
		}
		for(int i = 0; i < this.bondsFastBitArray.getSize(); i++) {
			if(this.bondsFastBitArray.get(i)) {
				IBond curBond = this.precursorMolecule.getStructureAsIAtomContainer().getBond(i);
				if(this.precursorMolecule.isAromaticBond(i)) curBond.setIsAromatic(true);
				for(IAtom atom : curBond.atoms()) {
					atom.setImplicitHydrogenCount(0);
					if(this.precursorMolecule.isAromaticBond(i)) atom.setIsAromatic(true);
					fragmentStructure.addAtom(atom);
				}
				fragmentStructure.addBond(curBond);
			}
		}
	//	loss of hydrogens
	//	MoleculeFunctions.prepareAtomContainer(fragmentStructure);
		
		return fragmentStructure;
	}
	
	public IAtomContainer getStructureAsIAtomContainer() {
		IChemObjectBuilder builder = DefaultChemObjectBuilder.getInstance();
		IAtomContainer fragmentStructure = builder.newInstance(IAtomContainer.class);
		if(this.atomsFastBitArray.cardinality() == 1) {
			fragmentStructure.addAtom(this.precursorMolecule.getStructureAsIAtomContainer().getAtom(this.atomsFastBitArray.getFirstSetBit()));
			return fragmentStructure;
		}
		for(int i = 0; i < this.bondsFastBitArray.getSize(); i++) {
			if(this.bondsFastBitArray.get(i)) {
				IBond curBond = this.precursorMolecule.getStructureAsIAtomContainer().getBond(i);
				for(IAtom atom : curBond.atoms()) {
					fragmentStructure.addAtom(atom);
				}
				fragmentStructure.addBond(curBond);
			}
		}
	//	loss of hydrogens
	//	MoleculeFunctions.prepareAtomContainer(fragmentStructure);
		
		return fragmentStructure;
	}

	public int getNonHydrogenAtomCount() {
		return this.atomsFastBitArray.cardinality();
	}

	public int getNonHydrogenBondCount() {
		return this.bondsFastBitArray.cardinality();
	}

	public void setTreeDepth(byte treeDepth) {
		this.treeDepth = treeDepth;
	}
	
	public byte getTreeDepth() {
		return this.treeDepth;
	}

	public boolean equals(Object object) {
		if(this.atomsFastBitArray.equals(((DefaultBitArrayFragment)object).getAtomsFastBitArray())) return true;
		return false;
	}
	
	/**
	 * returns 0 if the mass of the current is equal to the mass of the argument fragment
	 * returns -1 if the mass of the current is smaller to the mass of the argument fragment
	 * returns 1 if the mass of the current is greater to the mass of the argument fragment
	 * @throws AtomTypeNotKnownFromInputListException 
	 */
	public byte shareEqualProperties(IFragment fragment) {
		if(this.getMonoisotopicMass() == fragment.getMonoisotopicMass()) return 0;
		if(this.getMonoisotopicMass() > fragment.getMonoisotopicMass()) return 1;
		return -1;
	}

	public int[] getBrokenBondIndeces() {
		return this.brokenBondsFastBitArray.getSetIndeces();
	}
	
	public DefaultBitArrayFragment clone() {
		DefaultBitArrayFragment clone = new DefaultBitArrayFragment((BitArrayPrecursor)this.precursorMolecule, this.atomsFastBitArray.clone(), this.bondsFastBitArray.clone(), this.brokenBondsFastBitArray.clone());
	//	clone.setID(this.ID);
		clone.setNumberHydrogens(this.getNumberHydrogens());
		clone.setTreeDepth(this.treeDepth);
		return clone;
	}
	
	public void nullify() {
		super.nullify();
		this.atomsFastBitArray.nullify();
		this.bondsFastBitArray.nullify();
		this.brokenBondsFastBitArray.nullify();
	}

	/**
	 * is the argument fragment a real substructure of the current fragment
	 */
	public boolean isRealSubStructure(IFragment molecularStructure) {
		FastBitArray currentAtomsFastBitArray = ((DefaultBitArrayFragment)molecularStructure).getAtomsFastBitArray();
		if(currentAtomsFastBitArray.getSize() != this.atomsFastBitArray.getSize()) return false; 
		int currentAtomsFastBitArrayCardinality = 0;
		int thisAtomsFastBitArrayCardinality = 0;
		for(int i = 0; i < this.atomsFastBitArray.getSize(); i++) {
			boolean currentAtomsFastBitArrayValue = currentAtomsFastBitArray.get(i);
			boolean thisAtomsFastBitArrayValue = this.atomsFastBitArray.get(i);
			if(thisAtomsFastBitArrayValue) thisAtomsFastBitArrayCardinality++;
			if(currentAtomsFastBitArrayValue) currentAtomsFastBitArrayCardinality++;
			if(!thisAtomsFastBitArrayValue && currentAtomsFastBitArrayValue) return false;
		}
		if(thisAtomsFastBitArrayCardinality == currentAtomsFastBitArrayCardinality) return false;
		return true;
	}

	public boolean isSubStructure(IFragment molecularStructure) {
		FastBitArray currentAtomsFastBitArray = ((DefaultBitArrayFragment)molecularStructure).getAtomsFastBitArray();
		if(currentAtomsFastBitArray.getSize() != this.atomsFastBitArray.getSize()) return false; 
		for(int i = 0; i < this.atomsFastBitArray.getSize(); i++) {
			boolean currentAtomsFastBitArrayValue = currentAtomsFastBitArray.get(i);
			boolean thisAtomsFastBitArrayValue = this.atomsFastBitArray.get(i);
			if(!thisAtomsFastBitArrayValue && currentAtomsFastBitArrayValue) return false;
		}
		return true;
	}
	
	public int[] getUniqueBrokenBondIndeces(IFragment molecularStructure) {
		FastBitArray currentBrokenBondFastBitArray = ((DefaultBitArrayFragment)molecularStructure).getBrokenBondsFastBitArray();
		int numUniqueBondsSet = 0;
		for(int i = 0; i < currentBrokenBondFastBitArray.getSize(); i++) {
			if(currentBrokenBondFastBitArray.get(i) && !this.brokenBondsFastBitArray.get(i))
				numUniqueBondsSet++;
		}
		int[] uniqueBrokenBondIndeces = new int[numUniqueBondsSet];
		for(int i = 0; i < currentBrokenBondFastBitArray.getSize(); i++) {
			int index = 0;
			if(currentBrokenBondFastBitArray.get(i) && !this.brokenBondsFastBitArray.get(i)) {
				uniqueBrokenBondIndeces[index] = i;
			}	
		}
		return uniqueBrokenBondIndeces;
	}

	@Override
	public String getAtomsInfo() {
		return this.atomsFastBitArray.toString();
	}

	public IFragment getDifferenceFragment(IFragment molecularStructure) {
		FastBitArray diffFastBitArray = this.getAtomsFastBitArray().getDiff(((DefaultBitArrayFragment)molecularStructure).getAtomsFastBitArray());
		DefaultBitArrayFragment diffFragment = new DefaultBitArrayFragment((BitArrayPrecursor)this.precursorMolecule, diffFastBitArray);
		if(!diffFragment.isConnected()) return null;
		return diffFragment;
	}

	public IFragment getDifferenceFragment() {
		FastBitArray complete = new FastBitArray(this.getAtomsFastBitArray().getSize(), true);
		FastBitArray diffFastBitArray = complete.getDiff(this.getAtomsFastBitArray());
		DefaultBitArrayFragment diffFragment = 
				new DefaultBitArrayFragment((BitArrayPrecursor)this.precursorMolecule, diffFastBitArray);
		if(!diffFragment.isConnected()) return null;
		return diffFragment;
	}
	
	public boolean isConnected() {
		if(this.atomsFastBitArray.cardinality() == 1) return true;
		BitArrayPrecursor pre = (BitArrayPrecursor)this.precursorMolecule;
		FastBitArray foundAtoms = new FastBitArray(this.atomsFastBitArray.getSize(), false);
		java.util.LinkedList<Integer> toCheck = new java.util.LinkedList<Integer>();
		toCheck.add(this.atomsFastBitArray.getFirstSetBit());
		while(toCheck.size() != 0) {
			int currentAtomIndex = toCheck.poll();
			short[] neighbors = pre.getConnectedAtomIndecesOfAtomIndex((short)currentAtomIndex);
			for(int k = 0; k < neighbors.length; k++) {
				if(this.atomsFastBitArray.get(neighbors[k]) && !foundAtoms.get(neighbors[k])) { 
					foundAtoms.set(neighbors[k]);
					toCheck.add((int)neighbors[k]);
				}
			}
		}
		if(foundAtoms.equals(this.atomsFastBitArray)) return true;
		return false;
	}
	
	@Override
	public String getBondsInfo() {
		return this.bondsFastBitArray.toString();
	}

	@Override
	public String getBrokenBondsInfo() {
		return this.brokenBondsFastBitArray.toString();
	}

}
