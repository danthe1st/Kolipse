package io.github.danthe1st.kolipse.editor.display.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.filebuffers.IDocumentSetupParticipantExtension;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.statushandlers.StatusManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import io.github.danthe1st.kolipse.compiler.KotlinCompilationParticipant;
import io.github.danthe1st.kolipse.nature.KotlinProjectNature;

public class ValidatorDocumentSetupParticipant implements IDocumentSetupParticipant, IDocumentSetupParticipantExtension {
	private static final Bundle BUNDLE = FrameworkUtil.getBundle(ValidatorDocumentSetupParticipant.class);
	
	private static final String MARKER_ATTRIBUTE = "kolipse_marker";
	
	public static final class DocumentValidator implements IDocumentListener {
		private final IFile file;
		private List<IMarker> markers = new ArrayList<>();
		private Path validateFile;
		private Path validateOutputDir;
		
		private ExecutorService compilerPool = new ThreadPoolExecutor(0, 1, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>() {
			@Override
			public void put(Runnable e) throws InterruptedException {
				clear();
				super.put(e);
			}
		}, r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("Kolipse Kotlin compiler thread");
			return t;
		});
		
		public DocumentValidator(IFile file) throws IOException {
			this.file = file;
			try{
				for(IMarker marker : file.findMarkers(null, true, 0)){
					if(marker.getAttribute(MARKER_ATTRIBUTE, false)){
						markers.add(marker);
					}
				}
				
			}catch(CoreException e){
				throw new RuntimeException(e);
			}
			
			Path validateDir = BUNDLE.getDataFile("kotlin-validate").toPath();
			if(!Files.exists(validateDir)){
				Files.createDirectory(validateDir);
			}
			validateFile = Files.createTempFile(validateDir, "validate", ".kt");
			validateOutputDir = Files.createTempDirectory(validateDir, "validate");
		}
		
		@Override
		public void documentChanged(DocumentEvent event) {
			try{
				// ResourcesPlugin.getWorkspace().getRoot().get
				IProject p = file.getProject();
				if(!p.isNatureEnabled(KotlinProjectNature.NATURE_ID)){
					removeMarkersAndCreateProblemMarker(IMarker.SEVERITY_WARNING, "Kotlin nature is is not added to this project");
					return;
				}
				IJavaProject project = JavaCore.create(p);
				if(project == null){
					removeMarkersAndCreateProblemMarker(IMarker.SEVERITY_ERROR, "not a Java project");
					return;
				}
				if(!project.isOnClasspath(file)){
					removeMarkersAndCreateProblemMarker(IMarker.SEVERITY_WARNING, "not in classpath");
					return;
				}
				
				KotlinProjectNature kotlinProjectNature = (KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID);
				if(kotlinProjectNature.getKotlinCompilerPath() == null){// TODO revalidate when Kotlin compiler is set
					IMarker marker = removeMarkersAndCreateProblemMarker(IMarker.SEVERITY_ERROR, "Kotlin compiler is not configured");
					marker.setAttribute("kolipse_type", "KOTLIN_COMPILER_MISSING");
					marker.setAttribute(IMarker.CHAR_START, 0);
					marker.setAttribute(IMarker.LINE_NUMBER, 0);
					marker.setAttribute(IMarker.CHAR_END, event.getDocument().getLength());
				}else{
					compilerPool.execute(() -> tryCompileAndDisplayErrors(event, project));
				}
			}catch(CoreException e){
				e.printStackTrace();
			}
		}
		
		private IMarker removeMarkersAndCreateProblemMarker(int severity, String msg) throws CoreException {
			removeMarkers();
			return createProblemMarker(severity, msg);
		}
		
		private IMarker createProblemMarker(int severity, String msg) throws CoreException {
			IMarker marker = file.createMarker(KotlinProjectNature.KOLIPSE_MARKER_ID);
			markers.add(marker);
			marker.setAttribute(MARKER_ATTRIBUTE, true);
			marker.setAttribute(IMarker.SEVERITY, severity);
			marker.setAttribute(IMarker.MESSAGE, msg);
			return marker;
		}
		
		private void removeMarkers() throws CoreException {
			for(Iterator<IMarker> it = markers.iterator(); it.hasNext();){
				IMarker marker = it.next();
				marker.delete();
				it.remove();
			}
		}
		
		private void tryCompileAndDisplayErrors(DocumentEvent event, IJavaProject project) {
			try{
				Files.writeString(validateFile, event.getDocument().get());
				String validateFilePath = validateFile.normalize().toString();
				Process process = KotlinCompilationParticipant.compile(project, Collections.singletonList(validateFilePath), validateOutputDir, List.of("-Xno-optimize", "-Xno-optimized-callable-references"));
				try{
					process.waitFor();
				}catch(InterruptedException e){
					Thread.currentThread().interrupt();
					process.destroy();
				}
				removeMarkers();
				try(BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))){
					String line;
					while((line = br.readLine()) != null){
						if(line.startsWith(validateFilePath + ":")){
							String[] errorStringSplit = line.substring(validateFilePath.length() + 1).split(":", 4);
							try{
								int lineNumber = Integer.parseInt(errorStringSplit[0]);
								int col = Integer.parseInt(errorStringSplit[1]);
								String type = errorStringSplit[2].trim();
								String err = errorStringSplit[3];
								int severity;
								switch(type) {
									case "error":
										severity = IMarker.SEVERITY_ERROR;
										break;
									case "warning":
										severity = IMarker.SEVERITY_WARNING;
										break;
									default:
										System.out.println("unknown severity: " + type);
										severity = IMarker.SEVERITY_INFO;
										break;
								}
								IMarker marker = createProblemMarker(severity, err.trim());
								marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
								int offset = event.getDocument().getLineOffset(lineNumber - 1) + col - 1;
								marker.setAttribute(IMarker.CHAR_START, offset);
								marker.setAttribute(IMarker.CHAR_END, offset + 1);
							}catch(ArrayIndexOutOfBoundsException | NumberFormatException e){
								createProblemMarker(IMarker.SEVERITY_ERROR, "Error parsing Kotlin compiler output: " + line);
								StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "Error parsing Kotlin compiler output: " + line, e));
							}
						}else if(line.startsWith("error:")){
							createProblemMarker(IMarker.SEVERITY_ERROR, line.substring("error:".length()));
						}else if(line.startsWith("warning:")){
							createProblemMarker(IMarker.SEVERITY_WARNING, line.substring("warning:".length()));
						}
					}
				}
			}catch(Exception e){
				try{
					IMarker marker = createProblemMarker(IMarker.SEVERITY_ERROR, "An error occured trying to compile Kotlin code");
					try(StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw)){
						e.printStackTrace(pw);
						marker.setAttribute(IMarker.MESSAGE, "Compilation error\n" + sw);
					}
					StatusManager.getManager().handle(new Status(IStatus.ERROR, getClass(), "An error occured trying to compile Kotlin code", e));
				}catch(IOException | CoreException e1){
					e1.addSuppressed(e);
					throw new RuntimeException(e1);
				}
			}
		}
		
		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
		}
	}
	
	@Override
	public void setup(IDocument document) {
		// covered by IDocumentSetupParticipantExtension
	}
	
	@Override
	public void setup(IDocument document, IPath location, LocationKind locationKind) {
		try{
			if(locationKind == LocationKind.IFILE){
				IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(location);
				document.addDocumentListener(new DocumentValidator(file));
			}
		}catch(IOException e){
			throw new RuntimeException(e);
		}
	}
	
	// TODO delete validate directories on finish
}
