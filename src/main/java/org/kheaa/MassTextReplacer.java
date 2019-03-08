package org.kheaa;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDStream;

import javax.swing.*;

public class MassTextReplacer {

    public static void main(String[] args) {
        File inputFile = getInputFile(args);
        String searchString = getSearchString();
        String replaceString = getReplaceString();
        if (inputFile.exists()) {
            try (PDDocument document = PDDocument.load(inputFile)){
                PDDocument newDoc = replaceText(document, searchString, replaceString);

                prepareOutputDir();

                saveCloseCurrent(inputFile.getName(), newDoc);
            } catch (IOException ioe) {
                displayMessage("IO Error: \n" + ioe.getMessage(), "Error", true, -1);
            }
        } else {
            displayMessage("No file provided or file does not exist", "Error", true, -1);
        }
    }

    private static String getReplaceString() {
        return "P.O. Box 4321";
    }

    private static String getSearchString() {
        return "P.O. Box 1234";
    }

    public static PDDocument replaceText(PDDocument document, String searchString, String replacement) throws IOException {
        if (searchString.isEmpty() || replacement.isEmpty()) {
            return document;
        }
        PDPageTree pages = document.getDocumentCatalog().getPages();
        for (PDPage page : pages) {
            PDFStreamParser parser = new PDFStreamParser(page);
            parser.parse();
            List tokens = parser.getTokens();
            for (int j = 0; j < tokens.size(); j++) {
                Object next = tokens.get(j);
                if (next instanceof Operator) {
                    Operator op = (Operator) next;
                    //Tj and TJ are the two operators that display strings in a PDF
                    if (op.getName().equals("Tj")) {
                        // Tj takes one operator and that is the string to display so lets update that operator
                        COSString previous = (COSString) tokens.get(j - 1);
                        String string = previous.getString();
                        string = string.replaceFirst(searchString, replacement);
                        previous.setValue(string.getBytes());
                    } else if (op.getName().equals("TJ")) {
                        COSArray previous = (COSArray) tokens.get(j - 1);
                        for (int k = 0; k < previous.size(); k++) {
                            Object arrElement = previous.getObject(k);
                            if (arrElement instanceof COSString) {
                                COSString cosString = (COSString) arrElement;
                                String string = cosString.getString();
                                string = string.replaceAll(searchString, replacement);
                                cosString.setValue(string.getBytes());
                            }
                        }
                    }
                }
            }
            // now that the tokens are updated we will replace the page content stream.
            PDStream updatedStream = new PDStream(document);
            OutputStream out = updatedStream.createOutputStream();
            ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
            tokenWriter.writeTokens(tokens);
            page.setContents(updatedStream);
            out.close();
        }
        return document;
    }

    private static File getInputFile(String[] args) {
        File file = new File("");

        if (args.length != 1) {

            final JFileChooser fileChooser = new JFileChooser();

            int chooserResult = fileChooser.showOpenDialog(null);

            if (chooserResult == JFileChooser.APPROVE_OPTION) {
                file = fileChooser.getSelectedFile();
                System.out.println("User approved. File: " + file.getName());
            }
        } else {
            file = new File(args[0]);
            System.out.println("File provided via command line. File: " + file.getName());
        }

        return file;
    }

    private static void prepareOutputDir() {
        File outputdir = new File("output");

        if(!outputdir.isDirectory() && !outputdir.exists()) {
            try {
                outputdir.mkdir();
            } catch (SecurityException se) {
                displayMessage(
                        "You do not have permission to create the output directory: " + se.getMessage(),
                        "Error", true, -1);
            }
        }

        for (File file : Objects.requireNonNull(outputdir.listFiles())){
            file.delete();
        }
    }

    private static void saveCloseCurrent(String filename, PDDocument outputDocument)
            throws IOException
    {
        // save to new output file
        if (filename != null)
        {
            // save document into file
            File f = new File("output/" + filename + ".pdf");
            if (f.exists())
            {
                displayMessage("File " + f + " already exists!", "Error", true, -1);
            }
            outputDocument.save(f);
            outputDocument.close();
        }
    }

    private static void displayMessage(String message) {
        displayMessage(message, "Message");
    }

    private static void displayMessage(String message, String title) {
        displayMessage(message, title, false, 0);
    }

    private static void displayMessage(String message, String title, boolean exit, int code) {
        JOptionPane.showMessageDialog(
                null,
                message ,
                title,
                JOptionPane.PLAIN_MESSAGE );
        if (exit) {
            System.exit(code);
        }
    }
}
