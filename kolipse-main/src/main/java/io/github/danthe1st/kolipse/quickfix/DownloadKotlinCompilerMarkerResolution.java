package io.github.danthe1st.kolipse.quickfix;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.core.runtime.IProgressMonitor;

final class DownloadKotlinCompilerMarkerResolution extends AbstractKotlinCompilerMarkerResolution {
	// TODO allow downloading multiple different compilers
	
	@Override
	public String getLabel() {
		return "Download Kotlin Compiler";
	}
	
	@Override
	public String getDescription() {
		return "Download Kotlin compiler binaries";
	}
	
	@Override
	protected Path getCompilerPath(IProgressMonitor monitor) throws Exception {
		Path compilerDirectory = MissingKotlinCompilerQuickFixProcessor.BUNDLE.getDataFile("kotlin-compiler").toPath();
		if(!Files.exists(compilerDirectory)){
			Path tempCompilerDirectory = MissingKotlinCompilerQuickFixProcessor.BUNDLE.getDataFile("kotlin-compiler").toPath();
			if(Files.exists(tempCompilerDirectory)){
				try(Stream<Path> s = Files.walk(tempCompilerDirectory)){
					s.sorted(Comparator.reverseOrder()).forEach(t -> {
						try{
							Files.delete(t);
						}catch(IOException e){
							throw new UncheckedIOException(e);
						}
					});
				}
			}
			Files.createDirectories(tempCompilerDirectory);
			HttpURLConnection con = (HttpURLConnection) new URL("https://github.com/JetBrains/kotlin/releases/download/v1.7.10/kotlin-compiler-1.7.10.zip").openConnection();
			final int segments = 1024;
			final long totalLen = con.getContentLengthLong();
			try(CountingInputStream cis = new CountingInputStream(new BufferedInputStream(con.getInputStream()));
					ZipInputStream zis = new ZipInputStream(cis)){
				ZipEntry entry;
				while((entry = zis.getNextEntry()) != null){
					if(entry.isDirectory()){
						Files.createDirectory(tempCompilerDirectory.resolve(entry.getName()));
					}else{
						Files.copy(zis, tempCompilerDirectory.resolve(entry.getName()));
					}
					if(monitor != null){
						monitor.worked((int) (segments * cis.getCounter() / totalLen));
					}
				}
			}
			Files.move(tempCompilerDirectory, compilerDirectory);
		}
		return compilerDirectory.resolve("kotlinc/bin/kotlinc" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".bat" : ""));
	}
}