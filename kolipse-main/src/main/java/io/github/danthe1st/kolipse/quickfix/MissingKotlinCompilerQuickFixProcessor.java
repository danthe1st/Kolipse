package io.github.danthe1st.kolipse.quickfix;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import io.github.danthe1st.kolipse.nature.KotlinProjectNature;

public class MissingKotlinCompilerQuickFixProcessor implements IMarkerResolutionGenerator {
	
	static final Bundle BUNDLE = FrameworkUtil.getBundle(MissingKotlinCompilerQuickFixProcessor.class);
	
	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		IProject project = marker.getResource().getProject();
		try{
			if(project.hasNature(KotlinProjectNature.NATURE_ID)){
				KotlinProjectNature nature = (KotlinProjectNature) project.getNature(KotlinProjectNature.NATURE_ID);
				String problem = marker.getAttribute("kolipse_type", "");
				if("KOTLIN_COMPILER_MISSING".equals(problem)){
					return new IMarkerResolution[] {
							new DownloadKotlinCompilerMarkerResolution(),
							new SelectKotlinCompilerMarkerResolution()
					};
				}
			}
		}catch(CoreException e){
			// ignore
		}
		return new IMarkerResolution[0];
	}
	
}
