package io.github.danthe1st.kolipse.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.core.compiler.IProblem;

import io.github.danthe1st.kolipse.nature.KotlinProjectNature;

public class KotlinCompilationParticipant extends CompilationParticipant {
	// TODO incremental compilation?
	
	private static final String MARKER_ATTRIBUTE = "kolipse_marker";
	private List<IMarker> markers = new ArrayList<>();
	
	private void removeMarkers() throws CoreException {
		for(Iterator<IMarker> it = markers.iterator(); it.hasNext();){
			IMarker marker = it.next();
			marker.delete();
			it.remove();
		}
	}
	
	boolean firstBuild = true;
	
	@Override
	public int aboutToBuild(IJavaProject project) {
		try{
			KotlinProjectNature kotlinNature = (KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID);
			if(firstBuild){
				firstBuild = false;
				for(IMarker marker : project.getProject().findMarkers(null, true, 0)){
					if(marker.getAttribute(MARKER_ATTRIBUTE, false)){
						markers.add(marker);
					}
				}
			}else{
				removeMarkers();
			}
			IClasspathEntry[] resolvedClasspath;
			try{
				resolvedClasspath = project.getResolvedClasspath(true);
				List<String> kotlinSources = new ArrayList<>();
				for(IClasspathEntry classpathEntry : resolvedClasspath){
					if(classpathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE){
						try(Stream<Path> s = Files.walk(project.getProject().getFile(classpathEntry.getPath().lastSegment()).getLocation().toFile().toPath())){
							kotlinSources.addAll(
									s
										.filter(Files::isRegularFile)
										.filter(p -> p.toString().endsWith(".kt"))
										.map(p -> p.toAbsolutePath().toString())
										.collect(Collectors.toList())
							);
						}
					}
				}
				if(!kotlinSources.isEmpty()){
					try(Stream<Path> s = Files.walk(kotlinNature.getKotlinOutputFullPath())){
						s
							.skip(1)
							.sorted(Comparator.reverseOrder())
							.forEach(t -> {
								try{
									Files.delete(t);
								}catch(IOException e){
									throw new UncheckedIOException("Cannot delete Kotlin output directory", e);
								}
							});
					}
					
					try(ByteArrayOutputStream baos = new ByteArrayOutputStream();
							PrintStream ps = new PrintStream(baos)){
						String result = kotlinNature.getKotlinCompiler().compile(ps, project, kotlinSources, kotlinNature.getKotlinOutputFullPath(), Collections.emptyList());
						if(!"OK".equals(result)){
							IMarker marker = project.getProject().createMarker("org.eclipse.jdt.core.problem");
							markers.add(marker);
							marker.setAttribute(MARKER_ATTRIBUTE, true);
							marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
							marker.setAttribute(IMarker.MESSAGE, "Kolipse: Compiling Kotlin code failed with result '" + result + "'. See the full error description for details.\n" + new String(baos.toByteArray()));
							marker.setAttribute(IJavaModelMarker.ID, IProblem.ExternalProblemNotFixable);
						}
					}catch(CoreException e){
						throw new RuntimeException(e);
					}
					project.getProject().getFile(kotlinNature.getKotlinOutputPath()).refreshLocal(IResource.DEPTH_ZERO, null);
					project.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
				}
			}catch(Exception e){
				throw new RuntimeException(e);
			}
			return super.aboutToBuild(project);
		}catch(CoreException e){
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void cleanStarting(IJavaProject project) {
		super.cleanStarting(project);
		try{
			KotlinProjectNature nature = (KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID);
			try(Stream<Path> s = Files.walk(nature.getKotlinOutputFullPath())){
				s
					.skip(1)
					.sorted(Comparator.reverseOrder())
					.forEach(t -> {
						try{
							Files.delete(t);
						}catch(IOException e){
							throw new RuntimeException(e);
						}
					});
			}
		}catch(CoreException | IOException e){
			throw new RuntimeException(e);
		}
		
	}
	
	@Override
	public boolean isActive(IJavaProject project) {
		try{
			return project.getProject().isNatureEnabled(KotlinProjectNature.NATURE_ID) && ((KotlinProjectNature) project.getProject().getNature(KotlinProjectNature.NATURE_ID)).getKotlinCompilerPath() != null;
		}catch(CoreException e){
			throw new RuntimeException(e);
		}
	}
}
