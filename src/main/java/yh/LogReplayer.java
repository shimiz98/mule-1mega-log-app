package yh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class LogReplayer {
	private static final Log log = LogFactory.getLog(LogReplayer.class);
	
	static class ReplayInfo {
		ZonedDateTime prevDateTime = null;
		long logCount = 0;
	}

	public static String replayZipLog(String logFile) {
		try (InputStream isZipFile = LogReplayer.class.getClassLoader().getResourceAsStream(logFile)) {
			try (ZipInputStream zipIn = new ZipInputStream(isZipFile)) {
				
				ZipEntry zipEntry = zipIn.getNextEntry();
				log.info(zipEntry);
				String result = replayInputStreamLog(zipIn);
				return result;
			} catch (IOException e) {
				throw new UncheckedIOException("ZipInputStream(): " + logFile, e);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("ClassLoader.getResourceAsStream(): " + logFile, e);
		}
	}
	
	public static String replayInputStreamLog(InputStream is) {
		try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			ReplayInfo replayInfo = new ReplayInfo();
			StringBuilder logRecord = new StringBuilder();
			while(true) {
				String line = r.readLine();
				if (line == null) {
					replayStringLog(logRecord.toString(), replayInfo);
					break;
				}
				if (isFirstLine(line)) {
					replayStringLog(logRecord.toString(), replayInfo);
					logRecord.setLength(0);
				} else {
					logRecord.append('\n'); // 改行文字はLF固定でreplayする
					logRecord.append(line);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("BufferedReader(): ", e);
		}
		return "ok";
	}
	
	public static boolean isFirstLine(String line) {
		return line.startsWith("[2023-");
	}

	private static final Pattern LOG1_PATTERN = Pattern.compile("\\[([^\\\\]].+)\\] ([^ ]+) (.*)");
	private static final DateTimeFormatter LOG2_DT_FORMATTER = DateTimeFormatter.ofPattern("uuuu MM dd HH:mm:ss.nnnnnnnnn");
	
	public static String replayStringLog(String logRecord, ReplayInfo replayInfo) {
		Matcher m = LOG1_PATTERN.matcher(logRecord);
		m.matches();
		log.info(m.group(2) + " " + logRecord);
		return "ok";
	}
	
}
