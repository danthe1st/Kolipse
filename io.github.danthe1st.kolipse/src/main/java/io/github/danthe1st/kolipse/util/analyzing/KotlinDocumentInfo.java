package io.github.danthe1st.kolipse.util.analyzing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

public class KotlinDocumentInfo {
	private String packageName = "";
	private Map<String, String> imports = new HashMap<>();
	private List<IRegion> commentRegions = new ArrayList<>();
	private int documentLength;
	
	public String getPackageName() {
		return packageName;
	}
	
	void setPackageName(String packageName) {
		this.packageName = packageName;
	}
	
	public String getImport(String shortName) {
		return imports.get(shortName);
	}
	
	void addImport(String toAdd) {
		imports.put(toAdd.substring(toAdd.lastIndexOf(".") + 1), toAdd);
	}
	
	void addCommentRegion(int offfset, int len) {
		commentRegions.add(new Region(offfset, len));
	}
	
	public boolean isInComment(int offset) {
		for(IRegion region : commentRegions){// TODO binary search
			if(region.getOffset() <= offset && region.getOffset() + region.getLength() >= offset){
				return true;
			}
		}
		return false;
	}
	
	void setDocumentLength(int documentLength) {
		this.documentLength = documentLength;
	}
	
	public Set<String> getImportShortNames() {
		return Collections.unmodifiableSet(imports.keySet());
	}
	
	public List<IRegion> getNormalSourceCodeRegions() {// TODO exclude strings etc
		List<IRegion> regions = new ArrayList<>();
		int start = 0;
		for(IRegion comment : commentRegions){
			if(start != comment.getOffset()){
				regions.add(new Region(start, comment.getOffset() - start));
			}
			start = comment.getOffset() + comment.getLength();
		}
		if(start != documentLength){
			regions.add(new Region(start, documentLength - start));
		}
		return regions;
	}
	
	public List<IRegion> getCommentRegions() {
		return Collections.unmodifiableList(commentRegions);
	}
}
