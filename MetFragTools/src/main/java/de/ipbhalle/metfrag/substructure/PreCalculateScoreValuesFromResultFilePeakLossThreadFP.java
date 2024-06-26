package de.ipbhalle.metfrag.substructure;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.ipbhalle.metfraglib.FastBitArray;
import de.ipbhalle.metfraglib.additionals.MoleculeFunctions;
import de.ipbhalle.metfraglib.database.LocalCSVDatabase;
import de.ipbhalle.metfraglib.database.LocalPSVDatabase;
import de.ipbhalle.metfraglib.exceptions.MultipleHeadersFoundInInputDatabaseException;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.interfaces.IDatabase;
import de.ipbhalle.metfraglib.interfaces.IPeakListReader;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.match.MassFingerprintMatch;
import de.ipbhalle.metfraglib.parameter.Constants;
import de.ipbhalle.metfraglib.parameter.SettingsChecker;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.scoreinitialisation.AutomatedLossFingerprintAnnotationScoreInitialiser;
import de.ipbhalle.metfraglib.scoreinitialisation.AutomatedPeakFingerprintAnnotationScoreInitialiser;
import de.ipbhalle.metfraglib.settings.MetFragGlobalSettings;
import de.ipbhalle.metfraglib.settings.Settings;
import de.ipbhalle.metfraglib.substructure.MassToFingerprintsHashMap;
import de.ipbhalle.metfraglib.substructure.FingerprintGroup;
import de.ipbhalle.metfraglib.substructure.MassToFingerprintGroupList;
import de.ipbhalle.metfraglib.substructure.MassToFingerprintGroupListCollection;
import de.ipbhalle.metfraglib.writer.CandidateListWriterCSV;

public class PreCalculateScoreValuesFromResultFilePeakLossThreadFP {

	public static int numberFinished = 0;
	public static java.util.Hashtable<String, String> argsHash;

	public static boolean getArgs(String[] args) {
		argsHash = new java.util.Hashtable<String, String>();
		for (String arg : args) {
			arg = arg.trim();
			String[] tmp = arg.split("=");
			if (!tmp[0].equals("parampath") && !tmp[0].equals("resultpath") && !tmp[0].equals("threads")
					&& !tmp[0].equals("output") && !tmp[0].equals("fingerprinttype")) {
				System.err.println("property " + tmp[0] + " not known.");
				return false;
			}
			if (argsHash.containsKey(tmp[0])) {
				System.err.println("property " + tmp[0] + " already defined.");
				return false;
			}
			argsHash.put(tmp[0], tmp[1]);
		}

		if (!argsHash.containsKey("parampath")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("resultpath")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("threads")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("output")) {
			System.err.println("no csv defined");
			return false;
		}
		if (!argsHash.containsKey("fingerprinttype")) {
			argsHash.put("fingerprinttype", "");
		}
		return true;
	}

	public static void main(String[] args) throws Exception {
		getArgs(args);

		String paramfolder = (String) argsHash.get("parampath");
		String resfolder = (String) argsHash.get("resultpath");
		String outputfolder = (String) argsHash.get("output");

		int numberThreads = Integer.parseInt(argsHash.get("threads"));
		String fingerprinttype = (String) argsHash.get("fingerprinttype");
		
		File _resfolder = new File(resfolder);
		File _paramfolder = new File(paramfolder);

		File[] resultFiles = _resfolder.listFiles();
		File[] paramFiles = _paramfolder.listFiles();

		ArrayList<ProcessThread> threads = new ArrayList<ProcessThread>();

		for (int i = 0; i < paramFiles.length; i++) {
			String id = paramFiles[i].getName().split("\\.")[0];
			int resultFileID = -1;
			for (int j = 0; j < resultFiles.length; j++) {
				if (resultFiles[j].getName().startsWith(id + ".")) {
					resultFileID = j;
					break;
				}
			}
			if (resultFileID == -1) {
				System.out.println(id + " not found as result.");
				continue;
			}
			Settings settings = getSettings(paramFiles[i].getAbsolutePath());
			settings.set(VariableNames.LOCAL_DATABASE_PATH_NAME, resultFiles[resultFileID].getAbsolutePath());

			SettingsChecker sc = new SettingsChecker();
			if (!sc.check(settings)) {
				System.out.println("Error checking settings for " + id);
				continue;
			}
			IPeakListReader peakListReader = (IPeakListReader) Class
					.forName((String) settings.get(VariableNames.METFRAG_PEAK_LIST_READER_NAME))
					.getConstructor(Settings.class).newInstance(settings);

			settings.set(VariableNames.PEAK_LIST_NAME, peakListReader.read());

			ProcessThread thread = new PreCalculateScoreValuesFromResultFilePeakLossThreadFP().new ProcessThread(
					settings, outputfolder, fingerprinttype);
			threads.add(thread);
		}
		System.out.println("preparation finished");

		ExecutorService executer = Executors.newFixedThreadPool(numberThreads);
		for (ProcessThread thread : threads) {
			executer.execute(thread);
		}
		executer.shutdown();
		while (!executer.isTerminated()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static synchronized void increaseNumberFinished() {
		numberFinished++;
		System.out.println("finished " + numberFinished);
	}

	public static Settings getSettings(String parameterfile) {
		File parameterFile = new File(parameterfile);
		MetFragGlobalSettings settings = null;
		try {
			settings = MetFragGlobalSettings.readSettings(parameterFile, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return settings;
	}

	public static MassFingerprintMatch getMatchByMass(ArrayList<?> matches, Double peakMass) {
		return getMatchByMass(matches, peakMass, false);
	}

	public static MassFingerprintMatch getMatchByMass(ArrayList<?> matches, Double peakMass, boolean debug) {
		for (int i = 0; i < matches.size(); i++) {
			MassFingerprintMatch match = (MassFingerprintMatch) matches.get(i);
			if (debug)
				System.out.println(match.getMass());
			if (match.getMass().equals(peakMass))
				return match;
		}
		return null;
	}

	class ProcessThread extends Thread {
		protected Settings settings;
		protected String outputFolder;
		protected String fingerprintType;

		/**
		 * 
		 * @param settings
		 * @param outputFolder
		 */
		public ProcessThread(Settings settings, String outputFolder, String fingerprintType) {
			this.settings = settings;
			this.outputFolder = outputFolder;
			this.fingerprintType = fingerprintType;
		}

		/**
		 * 
		 */
		public void run() {
			IDatabase db = null;
			String dbFilename = (String) settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME);
			System.out.println(dbFilename);
			if (dbFilename.endsWith("psv"))
				db = new LocalPSVDatabase(this.settings);
			else
				db = new LocalCSVDatabase(settings);
			ArrayList<String> ids = null;
			try {
				ids = db.getCandidateIdentifiers();
			} catch (MultipleHeadersFoundInInputDatabaseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			CandidateList candidates = null;
			try {
				candidates = db.getCandidateByIdentifier(ids);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			if (candidates.getNumberElements() == 0) {
				System.out.println(
						"No candidates found in " + (String) this.settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME));
				return;
			}

			System.out.println(dbFilename.replaceAll(".*/", "") + ": Read " + candidates.getNumberElements() + " candidates");
			
			AutomatedPeakFingerprintAnnotationScoreInitialiser initPeak = new AutomatedPeakFingerprintAnnotationScoreInitialiser();
			AutomatedLossFingerprintAnnotationScoreInitialiser initLoss = new AutomatedLossFingerprintAnnotationScoreInitialiser();
			try {
				initPeak.initScoreParameters(this.settings);
				initLoss.initScoreParameters(this.settings);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			postProcessScoreParametersPeak(this.settings, candidates);
			postProcessScoreParametersLoss(this.settings, candidates);

			for (int i = 0; i < candidates.getNumberElements(); i++) {
				this.settings.set(VariableNames.CANDIDATE_NAME, candidates.getElement(i));
				this.singlePostCalculatePeak(this.settings, candidates.getElement(i));
				this.singlePostCalculateLoss(this.settings, candidates.getElement(i));
				this.removeProperties(candidates.getElement(i));
			}

			CandidateListWriterCSV writer = new CandidateListWriterCSV();
			try {
				writer.write(candidates, (String) this.settings.get(VariableNames.SAMPLE_NAME), this.outputFolder,
						settings);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			increaseNumberFinished();
		}

		/**
		 * 
		 * @param settings
		 * @param candidates
		 */
		public void postProcessScoreParametersPeak(Settings settings, CandidateList candidates) {
			// to determine F_u
			MassToFingerprintsHashMap peakMassToFingerprints = new MassToFingerprintsHashMap();
			MassToFingerprintGroupListCollection peakToFingerprintGroupListCollection = (MassToFingerprintGroupListCollection) settings
					.get(VariableNames.PEAK_TO_FINGERPRINT_GROUP_LIST_COLLECTION_NAME);

			for (int k = 0; k < candidates.getNumberElements(); k++) {
				/*
				 * check whether the single run was successful
				 */
				ICandidate currentCandidate = candidates.getElement(k);
				String fps = "";
				try {
					// get explained peak-fragment assignments
					fps = (String) currentCandidate.getProperty("FragmentFingerprintOfExplPeaks" + this.fingerprintType);
					// get all non-explained peakss
					String nonExplainedPeaks = (String) currentCandidate.getProperty("NonExplainedPeaks");
					// add non explained peaks to match list
					if(!nonExplainedPeaks.equals("NA")) {
						StringBuilder fpsBuilder = new StringBuilder();
						// first add the explained peak-fragment assignments
						if(!fps.equals("NA")) fpsBuilder.append(fps);
						String[] tmp = nonExplainedPeaks.split(";");
						// non-explained masses get predefined fingerprint 0 and will be added now
						// add first non-explained mass
						if(tmp.length >= 1) {
							if(fpsBuilder.length() != 0) fpsBuilder.append(";");
							fpsBuilder.append(tmp[0]);
							fpsBuilder.append(":0");
						}
						// add all other non-explained masses
						for(int l = 1; l < tmp.length; l++) {
							fpsBuilder.append(";");
							fpsBuilder.append(tmp[l]);
							fpsBuilder.append(":0");
						}
						if(fpsBuilder.length() != 0) fps = fpsBuilder.toString();
					}
					if (fps.equals("NA")) {
						currentCandidate.setProperty("PeakMatchList", new ArrayList<MassFingerprintMatch>());
						continue;
					}
				} catch (Exception e) {
					System.err.println((String) settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME) + ": Error at candidate " + k);
				}
				
				String[] tmp = fps.split(";");
				ArrayList<MassFingerprintMatch> matchlist = new ArrayList<MassFingerprintMatch>();
				for (int i = 0; i < tmp.length; i++) {
					String[] tmp1 = tmp[i].split(":");
					MassFingerprintMatch match = new MassFingerprintMatch(Double.parseDouble(tmp1[0]), MoleculeFunctions.stringToFastBitArray(tmp1[1]));
					matchlist.add(match);
				}
				currentCandidate.setProperty("PeakMatchList", matchlist);
				for (int j = 0; j < matchlist.size(); j++) {
					MassFingerprintMatch match = matchlist.get(j);
					MassToFingerprintGroupList peakToFingerprintGroupList = peakToFingerprintGroupListCollection.getElementByPeak(match.getMass());
					if (peakToFingerprintGroupList == null)
						continue;
					FastBitArray currentFingerprint = new FastBitArray(match.getFingerprint());
					// check whether fingerprint was observed for current peak
					// mass in the training data
					if (!peakToFingerprintGroupList.containsFingerprint(currentFingerprint)) {
						// if not add the fingerprint to background by
						// addFingerprint function
						// addFingerprint checks also whether fingerprint was
						// already added
						peakMassToFingerprints.addFingerprint(match.getMass(), currentFingerprint);
					}
				}
			}
			
			double f_seen_matched = (double) settings.get(VariableNames.PEAK_FINGERPRINT_MATCHED_TUPLE_COUNT_NAME); // f_s
			double f_seen_non_matched = (double) settings.get(VariableNames.PEAK_FINGERPRINT_NON_MATCHED_TUPLE_COUNT_NAME); // f_s
			double f_unseen_matched = peakMassToFingerprints.getOverallMatchedSize(); // f_u
			double f_unseen_non_matched = peakMassToFingerprints.getOverallNonMatchedSize(); // f_u
			double sumFingerprintFrequencies = (double) settings.get(VariableNames.PEAK_FINGERPRINT_DENOMINATOR_COUNT_NAME); // \sum_N
													// 1
			// set value for denominator of P(f,m)
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append("sumFingerprintFrequencies " + sumFingerprintFrequencies + "\n");
			stringBuilder.append("f_seen_matched " + f_seen_matched + "\n");
			stringBuilder.append("f_unseen_matched " + f_unseen_matched + "\n");
			stringBuilder.append("f_seen_non_matched " + f_seen_non_matched + "\n");
			stringBuilder.append("f_unseen_non_matched " + f_unseen_non_matched + "\n");
			// annotated
			stringBuilder.append("PeakToFingerprints\n");
			for (int i = 0; i < peakToFingerprintGroupListCollection.getNumberElements(); i++) {
				MassToFingerprintGroupList groupList = peakToFingerprintGroupListCollection.getElement(i);
				stringBuilder.append(groupList.getPeakmz());
				// sum_f P(f,m)
				// calculate sum of MF_s (including the alpha count) and the
				// joint probabilities
				// at this stage getProbability() returns the absolute counts
				// from the annotation files
				for (int ii = 0; ii < groupList.getNumberElements(); ii++) {
					// first calculate P(f,m)
					if(groupList.getElement(ii).getFingerprint().getSize() != 1) stringBuilder.append(" " + groupList.getElement(ii).getProbability());
					else stringBuilder.append(" " + -1.0 * groupList.getElement(ii).getProbability());
				}
				// calculate the sum of probabilities for un-observed
				// fingerprints for the current mass
				stringBuilder.append(" bgsize:" + peakMassToFingerprints.getSizeMatched(groupList.getPeakmz()) + " bgsize:" + peakMassToFingerprints.getSizeNonMatched(groupList.getPeakmz()));
				stringBuilder.append("\n");
			}
			try {
				this.writeTempData(this.outputFolder + Constants.OS_SPECIFIC_FILE_SEPARATOR
						+ settings.get(VariableNames.SAMPLE_NAME) + "_data_peak.txt", stringBuilder.toString());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}

		public void postProcessScoreParametersLoss(Settings settings, CandidateList candidates) {
			// to determine F_u
			MassToFingerprintsHashMap lossMassToFingerprints = new MassToFingerprintsHashMap();
			MassToFingerprintGroupListCollection lossToFingerprintGroupListCollection = (MassToFingerprintGroupListCollection) settings
					.get(VariableNames.LOSS_TO_FINGERPRINT_GROUP_LIST_COLLECTION_NAME);
			Double mzppm = (Double) settings.get(VariableNames.RELATIVE_MASS_DEVIATION_NAME);
			Double mzabs = (Double) settings.get(VariableNames.ABSOLUTE_MASS_DEVIATION_NAME);

			for (int k = 0; k < candidates.getNumberElements(); k++) {
				/*
				 * check whether the single run was successful
				 */
				ICandidate currentCandidate = candidates.getElement(k);
				String fps = (String) currentCandidate.getProperty("LossFingerprintOfExplPeaks" + this.fingerprintType);
				String nonExplainedLosses = (String) currentCandidate.getProperty("NonExplainedLosses");
				
				// add non explained peaks to match list
				if(!nonExplainedLosses.equals("NA")) {
					StringBuilder fpsBuilder = new StringBuilder();
					if(!fps.equals("NA")) fpsBuilder.append(fps);
					String[] tmp = nonExplainedLosses.split(";");
					if(tmp.length >= 1) {
						if(fpsBuilder.length() != 0) fpsBuilder.append(";");
						fpsBuilder.append(tmp[0]);
						fpsBuilder.append(":0");
					}
					for(int l = 1; l < tmp.length; l++) {
						fpsBuilder.append(";");
						fpsBuilder.append(tmp[l]);
						fpsBuilder.append(":0");
					}
					if(fpsBuilder.length() != 0) fps = fpsBuilder.toString();
				}
				if (fps.equals("NA")) {
					currentCandidate.setProperty("LossMatchList", new ArrayList<MassFingerprintMatch>());
					continue;
				}
				String[] tmp = fps.split(";");
				ArrayList<MassFingerprintMatch> lossMatchlist = new ArrayList<MassFingerprintMatch>();
				try {
					for (int i = 0; i < tmp.length; i++) {
						String[] tmp1 = tmp[i].split(":");
						double mass = Double.parseDouble(tmp1[0]);
						MassToFingerprintGroupList matchingLossToFingerprintGroupList = lossToFingerprintGroupListCollection.getElementByPeak(mass, mzppm, mzabs);
						if (matchingLossToFingerprintGroupList == null)
							continue;
						MassFingerprintMatch match = new MassFingerprintMatch(mass, MoleculeFunctions.stringToFastBitArray(tmp1[1]));
						lossMatchlist.add(match);
						FastBitArray currentFingerprint = new FastBitArray(match.getFingerprint());
						// if(match.getMatchedPeak().getMass() < 60)
						// System.out.println(match.getMatchedPeak().getMass() +
						// " " + currentFingerprint + " " + fragSmiles);
						// check whether fingerprint was observed for current
						// peak mass in the training data
						if (!matchingLossToFingerprintGroupList.containsFingerprint(currentFingerprint)) {
							// if not add the fingerprint to background by
							// addFingerprint function
							// addFingerprint checks also whether fingerprint
							// was already added
							lossMassToFingerprints.addFingerprint(match.getMass(), currentFingerprint);
						}
					}
				} catch (Exception e) {
					System.err.println("error LossFingerprintOfExplPeaks" + this.fingerprintType + " "
							+ settings.get(VariableNames.LOCAL_DATABASE_PATH_NAME) + " "
							+ currentCandidate.getIdentifier());
					e.printStackTrace();
					return;
				}
				currentCandidate.setProperty("LossMatchList", lossMatchlist);
			}

			double f_seen_matched = (double) settings.get(VariableNames.LOSS_FINGERPRINT_MATCHED_TUPLE_COUNT_NAME); // f_s
			double f_seen_non_matched = (double) settings.get(VariableNames.LOSS_FINGERPRINT_NON_MATCHED_TUPLE_COUNT_NAME); // f_s
			double f_unseen_matched = lossMassToFingerprints.getOverallMatchedSize(); // f_u
			double f_unseen_non_matched = lossMassToFingerprints.getOverallNonMatchedSize(); // f_u
			double sumFingerprintFrequencies = (double) settings.get(VariableNames.LOSS_FINGERPRINT_DENOMINATOR_COUNT_NAME); // \sum_N
																					// \sum_Ln
																					// 1
			// set value for denominator of P(f,m)
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append("sumFingerprintFrequencies " + sumFingerprintFrequencies + "\n");
			stringBuilder.append("f_seen_matched " + f_seen_matched + "\n");
			stringBuilder.append("f_unseen_matched " + f_unseen_matched + "\n");
			stringBuilder.append("f_seen_non_matched " + f_seen_non_matched + "\n");
			stringBuilder.append("f_unseen_non_matched " + f_unseen_non_matched + "\n");

			stringBuilder.append("LossToFingerprints\n");
			for (int i = 0; i < lossToFingerprintGroupListCollection.getNumberElements(); i++) {
				MassToFingerprintGroupList groupList = lossToFingerprintGroupListCollection.getElement(i);
				stringBuilder.append(groupList.getPeakmz());
				// sum_f P(f,m)
				// calculate sum of MF_s (including the alpha count) and the
				// joint probabilities
				// at this stage getProbability() returns the absolute counts
				// from the annotation files
				for (int ii = 0; ii < groupList.getNumberElements(); ii++) {
					// first calculate P(f,m)
					if(groupList.getElement(ii).getFingerprint().getSize() != 1) stringBuilder.append(" " + groupList.getElement(ii).getProbability());
					else stringBuilder.append(" " + -1.0 * groupList.getElement(ii).getProbability());
					// sum_f P(f,m) -> for F_s
				}

				// calculate the sum of probabilities for un-observed
				// fingerprints for the current mass
				stringBuilder.append(" bgsize:" + lossMassToFingerprints.getSizeMatched(groupList.getPeakmz()) + " bgsize:" + lossMassToFingerprints.getSizeNonMatched(groupList.getPeakmz()));
				stringBuilder.append("\n");
			}
			try {
				this.writeTempData(this.outputFolder + Constants.OS_SPECIFIC_FILE_SEPARATOR
						+ settings.get(VariableNames.SAMPLE_NAME) + "_data_loss.txt", stringBuilder.toString());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}

		public void writeTempData(String filename, String string) throws IOException {
			BufferedWriter bwriter = new BufferedWriter(new FileWriter(new File(filename)));
			bwriter.write(string);
			bwriter.close();
		}
	
		public String getProbTypeString(ArrayList<Double> matchProb, ArrayList<Integer> matchType,
				ArrayList<Double> matchMasses) {
			String string = "NA";
			if (matchProb.size() >= 1) {
				string = matchType.get(0) + ":" + matchProb.get(0) + ":" + matchMasses.get(0);
			}
			for (int i = 1; i < matchProb.size(); i++) {
				string += ";" + matchType.get(i) + ":" + matchProb.get(i) + ":" + matchMasses.get(i);
			}
			return string;
		}

		public void singlePostCalculatePeak(Settings settings, ICandidate candidate) {
			// this.value = 0.0;
			// found - 1; non-found - 2 (fp="0"); alpha - 3; beta - 4
			MassToFingerprintGroupListCollection peakToFingerprintGroupListCollection = (MassToFingerprintGroupListCollection) settings
					.get(VariableNames.PEAK_TO_FINGERPRINT_GROUP_LIST_COLLECTION_NAME);

			ArrayList<?> matchlist = (ArrayList<?>) candidate.getProperty("PeakMatchList");
			// get foreground fingerprint observations (m_f_observed)
			StringBuilder matchProbTypes = new StringBuilder();
			for (int i = 0; i < peakToFingerprintGroupListCollection.getNumberElements(); i++) {
				// get f_m_observed
				MassToFingerprintGroupList peakToFingerprintGroupList = peakToFingerprintGroupListCollection.getElement(i);
				Double currentMass = peakToFingerprintGroupList.getPeakmz();
				MassFingerprintMatch currentMatch = getMatchByMass(matchlist, currentMass);
				// (fingerprintToMasses.getSize(currentFingerprint));
				if (currentMatch == null) { // no fingerprint annotated
					matchProbTypes.append(currentMass);
					FingerprintGroup fg = peakToFingerprintGroupList.getElementByFingerprint(new FastBitArray("0"));
					if(fg == null) {
						matchProbTypes.append(":4");
					} else {
						matchProbTypes.append(fg.getProbability());
						matchProbTypes.append(":2");
					}
				} else {
					FastBitArray currentFingerprint = new FastBitArray(currentMatch.getFingerprint());
					// ToDo: at this stage try to check all fragments not only
					// the best one
					// (p(m,f) + alpha) / sum_F(p(m,f)) + |F| * alpha
					double matching_prob = peakToFingerprintGroupList.getMatchingProbability(currentFingerprint);
					// |F|
					if (matching_prob != 0.0) {
						matchProbTypes.append(currentMass);
						matchProbTypes.append(":");
						matchProbTypes.append(matching_prob);
						if(currentFingerprint.getSize() != 1) matchProbTypes.append(":1");
						else matchProbTypes.append(":2");
					} else {
						if(currentFingerprint.equals(new FastBitArray("0")) && peakToFingerprintGroupList.getElementByFingerprint(currentFingerprint) == null) {
							matchProbTypes.append(currentMass);
							matchProbTypes.append(":4");
						} else {
							matchProbTypes.append(currentMass);
							matchProbTypes.append(":3");
						}
					}
				}
				if (i != (peakToFingerprintGroupListCollection.getNumberElements() - 1))
					matchProbTypes.append(";");
			}
			candidate.removeProperty("PeakMatchList");
			candidate.setProperty("AutomatedPeakFingerprintAnnotationScore_Probtypes", matchProbTypes.toString());
		}

		/**
		 * 
		 * @param settings
		 * @param candidate
		 */
		public void singlePostCalculateLoss(Settings settings, ICandidate candidate) {
			MassToFingerprintGroupListCollection lossToFingerprintGroupListCollection = (MassToFingerprintGroupListCollection) settings
					.get(VariableNames.LOSS_TO_FINGERPRINT_GROUP_LIST_COLLECTION_NAME);
			java.util.LinkedList<?> lossMassesFound = (java.util.LinkedList<?>) ((java.util.LinkedList<?>) this.settings
					.get(VariableNames.LOSS_MASSES_FOUND_PEAKLIST_NAME)).clone();
			Double mzppm = (Double) settings.get(VariableNames.RELATIVE_MASS_DEVIATION_NAME);
			Double mzabs = (Double) settings.get(VariableNames.ABSOLUTE_MASS_DEVIATION_NAME);

			ArrayList<?> matchlist = (ArrayList<?>) candidate.getProperty("LossMatchList");
			// get foreground fingerprint observations (m_f_observed)
			StringBuilder matchProbTypes = new StringBuilder();
			
			for (int i = 0; i < matchlist.size(); i++) {
				// match with mass of the spectrum
				MassFingerprintMatch currentMatch = (MassFingerprintMatch) matchlist.get(i);
				lossMassesFound.remove(lossMassesFound.indexOf(currentMatch.getMass()));
				// get f_m_observed
				MassToFingerprintGroupList lossToFingerprintGroupList = lossToFingerprintGroupListCollection.getElementByPeak(currentMatch.getMass(), mzppm, mzabs);
				FastBitArray currentFingerprint = new FastBitArray(currentMatch.getFingerprint());
				double matching_prob = lossToFingerprintGroupList.getMatchingProbability(currentFingerprint);
				if (matching_prob != 0.0) {
					matchProbTypes.append(currentMatch.getMass());
					matchProbTypes.append(":");
					matchProbTypes.append(matching_prob);
					if(currentFingerprint.getSize() != 1) matchProbTypes.append(":1");
					else matchProbTypes.append(":2");
				} else {
					matchProbTypes.append(currentMatch.getMass());
					if(currentFingerprint.getSize() != 1) matchProbTypes.append(":3"); 
					else matchProbTypes.append(":4");
				}
				matchProbTypes.append(";");
			}
			for (int i = 0; i < lossMassesFound.size(); i++) {
				Double currentMass = (Double) lossMassesFound.get(i);
				if (matchProbTypes.length() != 0 && matchProbTypes.charAt(matchProbTypes.length() - 1) != ';')
					matchProbTypes.append(";");
				matchProbTypes.append(currentMass);
				matchProbTypes.append(":4");
			}

			candidate.removeProperty("LossMatchList");
			candidate.setProperty("AutomatedLossFingerprintAnnotationScore_Probtypes", matchProbTypes.toString());
		}

		public void removeProperties(ICandidate candidate) {
			candidate.removeProperty("FragmentFingerprintOfExplPeaksCircularFingerprinter");
			candidate.removeProperty("SmilesOfExplPeaks");
			candidate.removeProperty("LossFingerprintOfExplPeaksLingoFingerprinter");
			candidate.removeProperty("FragmentFingerprintOfExplPeaksMACCSFingerprinter");
			candidate.removeProperty("LossAromaticSmilesOfExplPeaks");
			candidate.removeProperty("FragmentFingerprintOfExplPeaksLingoFingerprinter");
			candidate.removeProperty("MaximumTreeDepth");
			candidate.removeProperty("LossFingerprintOfExplPeaksShortestPathFingerprinter");
			candidate.removeProperty("LossFingerprintOfExplPeaksGraphOnlyFingerprinter");
			candidate.removeProperty("ExplPeaks");
			candidate.removeProperty("LossFingerprintOfExplPeaksMACCSFingerprinter");
			candidate.removeProperty("FragmentFingerprintOfExplPeaksCircularFingerprinter");
			candidate.removeProperty("FragmentFingerprintOfExplPeaksShortestPathFingerprinter");
			candidate.removeProperty("LossFingerprintOfExplPeaksCircularFingerprinter");
			candidate.removeProperty("FragmenterScore_Values");
			candidate.removeProperty("AromaticSmilesOfExplPeaks");
			candidate.removeProperty("FormulasOfExplPeaks");
			candidate.removeProperty("LossSmilesOfExplPeaks");
			candidate.removeProperty("FragmentFingerprintOfExplPeaksGraphOnlyFingerprinter");
		}
	}
}
