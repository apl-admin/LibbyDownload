package com.apl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Chapter {
	private List<String> fileList = new ArrayList<>();
	private Map<String, FileLocation> fileMap = new HashMap<>();
	private String title;

	public Chapter(String title) {
		this.title = title;
	}

	public void addFile(String fileName) {
		fileList.add(fileName);
	}

	public void addFileLocation(String type, String fileName, int filePosition) {
		FileLocation fileLocation = new FileLocation(fileName, filePosition);
		fileMap.put(type, fileLocation);
	}

	public List<String> getFileList() {
		return fileList;
	}

	public Map<String, FileLocation> getFileLocMap() {
		return fileMap;
	}

	public String getFirstFile() {
		if (fileList.size() > 0) {
			return fileList.get(0);
		} else {
			return "";
		}
	}

	public String getLastFile() {
		if (fileList.size() > 0) {
			return fileList.get(fileList.size() - 1);
		} else {
			return "";
		}
	}

	public int getStartLoc() {
		FileLocation fileLoc = getFileLocMap().get("start");
		if (fileLoc != null) {
			return fileLoc.getFilePosition();
		} else {
			return -1;
		}
	}

	public String getTitle() {
		return title;
	}
}
