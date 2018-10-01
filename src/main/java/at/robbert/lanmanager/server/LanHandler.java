package at.robbert.lanmanager.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class LanHandler extends AbstractHandler {

	public static final String MIME_TEXT_HTML_UTF8 = "text/html;charset=utf-8";
	public static final String MIME_TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8";
	private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
	private static final String MIME_APPLICATION_ZIP = "application/zip";

	private final File sourceFolder;

	public LanHandler(final File sourceFolder) {
		this.sourceFolder = sourceFolder;
	}

	@Override
	public void handle(final String target, final Request baseRequest, final HttpServletRequest request,
			final HttpServletResponse response) throws IOException, ServletException {
		File requestedFile = getRequestedFile(target);
		try {
			if (requestedFile == null) {
				requestedFile = new File("index.html");
				response.setContentType(MIME_TEXT_HTML_UTF8);
				response.setStatus(HttpServletResponse.SC_OK);
				final PrintWriter out = response.getWriter();
				out.println("<html><head><title>Files</title></head><body>");
				out.println("Available files:<br/>");

				final File[] files = sourceFolder.listFiles();
				Arrays.sort(files, Comparator.comparing(File::getName));

				for (final File file : files) {
					out.println("<a href=\"/" + file.getName() + "\">");
					out.println(file.getName() + "</a> (" + getSizeString(file) + ")<br/>");
				}
				out.println("</body></html>");

				logSendingDone(requestedFile, request.getRemoteAddr());

				baseRequest.setHandled(true);
			} else if (requestedFile.isFile()) {
				response.setContentType(MIME_APPLICATION_OCTET_STREAM);
				response.setStatus(HttpServletResponse.SC_OK);
				if (requestedFile.length() < Integer.MAX_VALUE) {
					response.setContentLength((int) requestedFile.length());
				}

				logSending(requestedFile, request.getRemoteAddr());
				try (FileInputStream inputStream = new FileInputStream(requestedFile)) {
					IOUtils.copy(inputStream, response.getOutputStream());
				}
				logSendingDone(requestedFile, request.getRemoteAddr());

				baseRequest.setHandled(true);
			} else if (requestedFile.isDirectory()) {
				response.setContentType(MIME_APPLICATION_ZIP);
				response.setStatus(HttpServletResponse.SC_OK);
				response.setHeader("Content-Disposition", "attachment; filename=\"" + requestedFile.getName() + ".zip"
						+ "\"");

				logSending(requestedFile, request.getRemoteAddr());
				ZipOutputStream zipOutputStream = null;
				try {
					zipOutputStream = new ZipOutputStream(response.getOutputStream());
					zipOutputStream.setLevel(0);
					zipFolder(zipOutputStream, "", requestedFile);
				} finally {
					zipOutputStream.finish();
				}
				logSendingDone(requestedFile, request.getRemoteAddr());

				baseRequest.setHandled(true);
			}
		} catch (final Exception ex) {
			logSendingCancelled(requestedFile, request.getRemoteAddr());
			throw ex;
		}
	}

	private void logSendingCancelled(final File requestedFile, final String ip) {
		String requestedFileName;
		if (requestedFile.isDirectory()) {
			requestedFileName = requestedFile.getName() + ".zip";
		} else {
			requestedFileName = requestedFile.getName();
		}
		System.out.println("Cancelled sending " + requestedFileName + " to " + ip + "");
	}

	private void logSendingDone(final File requestedFile, final String ip) {
		System.out.println("Done sending " + requestedFile + ".zip to " + ip + "");
	}

	private void logSending(final File requestedFile, final String ip) {
		System.out.println("Sending " + requestedFile + ".zip to " + ip + "...");
	}

	private String getSizeString(final File file) {
		final long size = getSize(file);
		return humanReadableByteCount(size, false);
	}

	private long getSize(final File file) {
		long size = 0;
		if (file.isFile()) {
			size += file.length();
		} else if (file.isDirectory()) {
			for (final File subFile : file.listFiles()) {
				size += getSize(subFile);
			}
		}
		return size;
	}

	public static String humanReadableByteCount(final long bytes, final boolean si) {
		final int unit = si ? 1000 : 1024;
		if (bytes < unit) {
			return bytes + " B";
		}
		final int exp = (int) (Math.log(bytes) / Math.log(unit));
		final String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	private void zipFolder(final ZipOutputStream zipOutputStream, final String path, final File file)
			throws IOException {
		if (file.isDirectory()) {
			for (final File subFile : file.listFiles()) {
				zipFolder(zipOutputStream, path + file.getName() + "/", subFile);
			}
		} else if (file.isFile()) {
			zipOutputStream.putNextEntry(new ZipEntry(path + file.getName()));
			try (FileInputStream inputStream = new FileInputStream(file)) {
				IOUtils.copy(inputStream, zipOutputStream);
			}
			zipOutputStream.closeEntry();
		}
	}

	private File getRequestedFile(final String target) {
		if (target.equals("/")) {
			return null;
		}
		final File file = new File(sourceFolder, "." + target);
		if (file.exists()) {
			return file;
		} else {
			return null;
		}
	}
}
