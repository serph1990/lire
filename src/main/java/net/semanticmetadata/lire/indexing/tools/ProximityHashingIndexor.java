/*
 * This file is part of the LIRE project: http://www.semanticmetadata.net/lire
 * LIRE is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * LIRE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LIRE; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * We kindly ask you to refer the any or one of the following publications in
 * any publication mentioning or employing Lire:
 *
 * Lux Mathias, Savvas A. Chatzichristofis. Lire: Lucene Image Retrieval –
 * An Extensible Java CBIR Library. In proceedings of the 16th ACM International
 * Conference on Multimedia, pp. 1085-1088, Vancouver, Canada, 2008
 * URL: http://doi.acm.org/10.1145/1459359.1459577
 *
 * Lux Mathias. Content Based Image Retrieval with LIRE. In proceedings of the
 * 19th ACM International Conference on Multimedia, pp. 735-738, Scottsdale,
 * Arizona, USA, 2011
 * URL: http://dl.acm.org/citation.cfm?id=2072432
 *
 * Mathias Lux, Oge Marques. Visual Information Retrieval using Java and LIRE
 * Morgan & Claypool, 2013
 * URL: http://www.morganclaypool.com/doi/abs/10.2200/S00468ED1V01Y201301ICR025
 *
 * Copyright statement:
 * ====================
 * (c) 2002-2013 by Mathias Lux (mathias@juggle.at)
 *  http://www.semanticmetadata.net/lire, http://www.lire-project.net
 *
 * Updated: 02.06.13 11:19
 */

package net.semanticmetadata.lire.indexing.tools;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.LireFeature;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.utils.LuceneUtils;
import net.semanticmetadata.lire.utils.SerializationUtils;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * The Indexor (yes, I know the hashFunctionsFileName sounds weird, but it should match the Extractor class, and not
 * the Lucene Indexing classes) reads data files created by the {@link Extractor}. They are added to
 * a given index. Note that the index is not overwritten, but the documents are appended.
 *
 * @author Mathias Lux, mathias@juggle.at
 *         Date: 08.03.13
 *         Time: 14:28
 */
public class ProximityHashingIndexor {
    protected LinkedList<File> inputFiles = new LinkedList<File>();
    protected String indexPath = null;
    private boolean overwriteIndex = true;
    protected static boolean verbose = true;
    int run = 0;
    int docCount = 0;
    HashSet<Integer> representativesID;
    ArrayList<LireFeature> representatives;

    protected Class featureClass = ColorLayout.class;

    public static void main(String[] args) throws IOException, IllegalAccessException, InstantiationException {
        ProximityHashingIndexor indexor = new ProximityHashingIndexor();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-i") || arg.startsWith("--input-file")) {
                // infile ...
                if ((i + 1) < args.length)
                    indexor.addInputFile(new File(args[i + 1]));
                else printHelp();
            } else if (arg.startsWith("-l") || arg.startsWith("--index")) {
                // index
                if ((i + 1) < args.length)
                    indexor.setIndexPath(args[i + 1]);
                else printHelp();
            } else if (arg.startsWith("-h")) {
                // help
                printHelp();
            } else if (arg.startsWith("-s")) {
                // silent ...
                verbose = false;
            } else if (arg.startsWith("-c")) {
                // list of input files within a file.
                if ((i + 1) < args.length) {
                    BufferedReader br = new BufferedReader(new FileReader(new File(args[i + 1])));
                    String file;
                    while ((file = br.readLine()) != null) {
                        if (file.trim().length() > 2) {
                            File f = new File(file);
                            if (f.exists()) indexor.addInputFile(f);
                            else System.err.println("Did not find file " + f.getCanonicalPath());
                        }
                    }
                } else printHelp();
            }
        }
        // check if there is an infile, an outfile and some features to extract.
        if (!indexor.isConfigured()) {
            printHelp();
        } else {
            indexor.run();
        }
    }

    protected boolean isConfigured() {
        boolean isConfigured = true;
        // check if there are input files and if they exist.
        if (inputFiles.size() > 0) {
            for (Iterator<File> iterator = inputFiles.iterator(); iterator.hasNext(); ) {
                File next = iterator.next();
                if (!next.exists()) {
                    isConfigured = false;
                    System.err.println("Input file " + next.getPath() + " does not exist.");
                }
            }
        }
        return isConfigured;
    }

    /**
     * Just prints help.
     */
    protected static void printHelp() {
        System.out.println("Help for the Indexor class.\n" +
                "===========================\n" +
                "This help text is shown if you start the Indexor with the '-h' option.\n" +
                "\n" +
                "Usage\n" +
                "=====\n" +
                "$> Indexor -i <input-file> -l <index-directory>\n" +
                "\n" +
                "or \n" +
                "\n" +
                "$> Indexor -c <file-list> -l <index-directory>\n" +
                "\n" +
                "with \n" +
                "\n" +
                "<input-file> ... \t\ta single output file of Extractor.\n" +
                "<index-directory> ...\tthe index to write the data to (it's appended).\n" +
                "<file-list> ...\t\t\ta file containing data files one per line.\n");
    }


    public void setFeatureClass(Class featureClass) {
        this.featureClass = featureClass;
    }

    public void run() {
        // do it ...
        try {
            IndexWriter indexWriter = LuceneUtils.createIndexWriter(indexPath, overwriteIndex, LuceneUtils.AnalyzerType.WhitespaceAnalyzer);
            for (Iterator<File> iterator = inputFiles.iterator(); iterator.hasNext(); ) {
                File inputFile = iterator.next();
                if (verbose) System.out.println("Processing " + inputFile.getPath() + ".");
                if (verbose) System.out.println("Counting images.");
                run = 0;
                readFile(indexWriter, inputFile);
                if (verbose) System.out.printf("%d images found in the data file.\n", docCount);
                int numberOfRepresentatives = 1000;  // TODO: clever selection.
                // select a number of representative "fixed stars" randomly from file
                if (numberOfRepresentatives > docCount / 10) numberOfRepresentatives = docCount / 10;
                if (verbose) System.out.printf("Selecting %d representative images for hashing.\n", numberOfRepresentatives);
                representativesID = new HashSet<Integer>(numberOfRepresentatives);
                while (representativesID.size() < numberOfRepresentatives) {
                    representativesID.add((int) Math.floor(Math.random() * (docCount - 1)));
                }
                representatives = new ArrayList<LireFeature>(numberOfRepresentatives);
                docCount = 0;
                run = 1;
                if (verbose) System.out.println("Now getting representatives from the data file.");
                readFile(indexWriter, inputFile);
                docCount = 0;
                run = 2;
                if (verbose) System.out.println("Finally we start the indexing process, please wait ...");
                readFile(indexWriter, inputFile);
                if (verbose) System.out.println("Indexing finished.");
            }
            indexWriter.commit();
            indexWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads data from a file and writes it to an index.
     *
     * @param indexWriter the index to write to.
     * @param inputFile   the input data for the process.
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    private void readFile(IndexWriter indexWriter, File inputFile) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        BufferedInputStream in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(inputFile)));
        byte[] tempInt = new byte[4];
        int tmp, tmpFeature, count = 0;
        byte[] temp = new byte[100 * 1024];
        // read file hashFunctionsFileName length:
        while (in.read(tempInt, 0, 4) > 0) {
            Document d = new Document();
            tmp = SerializationUtils.toInt(tempInt);
            // read file hashFunctionsFileName:
            in.read(temp, 0, tmp);
            String filename = new String(temp, 0, tmp);
            // normalize Filename to full path.
            filename = inputFile.getCanonicalPath().substring(0, inputFile.getCanonicalPath().lastIndexOf(inputFile.getName())) + filename;
            d.add(new StringField(DocumentBuilder.FIELD_NAME_IDENTIFIER, filename, Field.Store.YES));
//            System.out.print(filename);
            while ((tmpFeature = in.read()) < 255) {
//                System.out.print(", " + tmpFeature);
                LireFeature f = (LireFeature) Class.forName(Extractor.features[tmpFeature]).newInstance();
                // byte[] length ...
                in.read(tempInt, 0, 4);
                tmp = SerializationUtils.toInt(tempInt);
                // read feature byte[]
                in.read(temp, 0, tmp);
                f.setByteArrayRepresentation(temp, 0, tmp);
                addToDocument(f, d, Extractor.featureFieldNames[tmpFeature]);
//                d.add(new StoredField(Extractor.featureFieldNames[tmpFeature], f.getByteArrayRepresentation()));
            }
            if (run == 2) indexWriter.addDocument(d);
            docCount++;
//            if (count%1000==0) System.out.print('.');
//            if (count%10000==0) System.out.println(" " + count);
        }
        in.close();
    }

    /**
     * Overwrite this method if you want to filter the input, apply hashing, etc.
     *
     * @param feature          the current feature.
     * @param document         the current document.
     * @param featureFieldName the field hashFunctionsFileName of the feature.
     */
    protected void addToDocument(LireFeature feature, Document document, String featureFieldName) {
        if (run == 0) {
        } // just count documents
        else if (run == 1) { // Select the representatives ...
            if (representativesID.contains(docCount) && feature.getClass().getCanonicalName().equals(featureClass.getCanonicalName())) { // it's a representative.
                // put it into a temporary data structure ...
                representatives.add(feature);
            }
        } else if (run == 2) { // actual hashing: find the nearest representatives and put those as a hash into a document.
            if (feature.getClass().getCanonicalName().equals(featureClass.getCanonicalName())) { // it's a feature to be hashed
                document.add(new TextField(featureFieldName + "_hash", SerializationUtils.arrayToString(getHashes(feature)), Field.Store.YES));
            }
            document.add(new StoredField(featureFieldName, feature.getByteArrayRepresentation()));
        }
    }

    private int[] getHashes(LireFeature feature) {
        int maximumHits = 50;
        int[] result = new int[maximumHits];
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();
        float maxDistance = 0f;
        float tmpScore = 0f;
        int rep = 0;
        for (Iterator<LireFeature> iterator = representatives.iterator(); iterator.hasNext(); ) {
            LireFeature repFeature = iterator.next();
            tmpScore = repFeature.getDistance(feature);
            if (resultScoreDocs.size() < maximumHits) {
                resultScoreDocs.add(new SimpleResult(tmpScore, null, rep));
                maxDistance = Math.max(maxDistance, tmpScore);
            } else if (tmpScore < maxDistance) {
                resultScoreDocs.add(new SimpleResult(tmpScore, null, rep));
            }
            while (resultScoreDocs.size() > maximumHits) {
                resultScoreDocs.remove(resultScoreDocs.last());
                maxDistance = resultScoreDocs.last().getDistance();
            }
            rep++;
        }
        rep = 0;
        for (Iterator<SimpleResult> iterator = resultScoreDocs.iterator(); iterator.hasNext(); ) {
            SimpleResult next = iterator.next();
            result[rep] = next.getIndexNumber();
            rep++;
        }
        return result;
    }

    public void addInputFile(File inputFile) {
        this.inputFiles.add(inputFile);
    }

    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }
}
