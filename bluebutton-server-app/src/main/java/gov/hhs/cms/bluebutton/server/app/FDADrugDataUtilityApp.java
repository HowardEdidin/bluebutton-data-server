package gov.hhs.cms.bluebutton.server.app;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;

/**
 * A simple application that downloads the FDA NDC (national drug code) file;
 * unzips it and then converts it to UTF-8 format
 */
public final class FDADrugDataUtilityApp {

	/**
	 * Size of the buffer to read/write data
	 */
	private static final int BUFFER_SIZE = 4096;

	/**
	 * 
	 * The application entry point, which will receive all non-JVM command line
	 * options in the <code>args</code> array.
	 * 
	 * @param args
	 *            <p>
	 *            The non-JVM command line arguments that the application was
	 *            launched with. Must include:
	 *            </p>
	 *            <ol>
	 *            <li><code>OUTPUT_DIR</code>: the first (and only) argument for
	 *            this application must be the already-existing path to write the
	 *            NDC file out to</li>
	 *            </ol>
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("OUTPUT_DIR argument not specified for FDA NDC download.");
			System.exit(1);
		}
		if (args.length > 1) {
			System.err.println("Invalid arguments supplied for FDA NDC download.");
			System.exit(2);
		}

		Path outputPath = Paths.get(args[0]);
		if (!Files.isDirectory(outputPath)) {
			System.err.println("OUTPUT_DIR does not exist for FDA NDC download.");
			System.exit(3);
		}

		// download FDA NDC file
		Path downloadedNdcZipFile = Paths.get(outputPath.resolve("ndctext.zip").toFile().getAbsolutePath());
		try {
			// connectionTimeout, readTimeout = 10 seconds
			FileUtils.copyURLToFile(new URL("https://www.accessdata.fda.gov/cder/ndctext.zip"),
					new File(downloadedNdcZipFile.toFile().getAbsolutePath()), 10000,
					10000);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(4);
		}

		// unzip FDA NDC file
		unzip(downloadedNdcZipFile, outputPath);
		Files.move(Paths.get(outputPath.resolve("product.txt").toFile().getAbsolutePath()),
				Paths.get(outputPath.resolve("fda_products_cp1252.tsv").toFile().getAbsolutePath()), REPLACE_EXISTING);
		
		// convert file format from cp1252 to utf8
		CharsetDecoder inDec=Charset.forName("windows-1252").newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);

		CharsetEncoder outEnc=StandardCharsets.UTF_8.newEncoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);

		try
		(FileInputStream is = new FileInputStream(
				outputPath.resolve("fda_products_cp1252.tsv").toFile().getAbsolutePath());
			 BufferedReader reader=new BufferedReader(new InputStreamReader(is, inDec));
				FileOutputStream fw = new FileOutputStream(
						outputPath.resolve("fda_products_utf8.tsv").toFile().getAbsolutePath());
			 BufferedWriter out=new BufferedWriter(new OutputStreamWriter(fw, outEnc))) {

			 for(String in; (in = reader.readLine()) != null; ) {
				   out.write(in);
				   out.newLine();
		      }
		}


	}

	/**
	 * Extracts a zip file specified by the zipFilePath to a directory specified by
	 * destDirectory (will be created if does not exists)
	 * 
	 * @param zipFilePath
	 * @param destDirectory
	 * @throws IOException
	 */
	private static void unzip(Path zipFilePath, Path destDirectory) throws IOException {
		File destDir = new File(destDirectory.toFile().getAbsolutePath());
		if (!destDir.exists()) {
			destDir.mkdir();
		}
		ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath.toFile().getAbsolutePath()));
		ZipEntry entry = zipIn.getNextEntry();
		// iterates over entries in the zip file
		while (entry != null) {
			Path filePath = Paths.get(destDirectory.toFile().getAbsolutePath(), entry.getName());
			if (!entry.isDirectory()) {
				// if the entry is a file, extracts it
				extractFile(zipIn, filePath);
			} else {
				// if the entry is a directory, make the directory
				File dir = new File(filePath.toFile().getAbsolutePath());
				dir.mkdir();
			}
			zipIn.closeEntry();
			entry = zipIn.getNextEntry();
		}
		zipIn.close();
	}

	/**
	 * Extracts a zip entry (file entry)
	 * 
	 * @param zipIn
	 * @param filePath
	 * @throws IOException
	 */
	private static void extractFile(ZipInputStream zipIn, Path filePath) throws IOException {
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath.toFile().getAbsolutePath()));
		byte[] bytesIn = new byte[BUFFER_SIZE];
		int read = 0;
		while ((read = zipIn.read(bytesIn)) != -1) {
			bos.write(bytesIn, 0, read);
		}
		bos.close();
	}
}
