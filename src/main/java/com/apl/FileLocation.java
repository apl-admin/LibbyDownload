package com.apl;

public class FileLocation {
	private String fileName;

	private int filePosition = -1;

	public FileLocation(String fileName, int filePosition) {
		super();
		this.fileName = fileName;
		this.filePosition = filePosition;
	}

	public String getFileName() {
		return fileName;
	}

	public int getFilePosition() {
		return filePosition;
	}

	@Override
	public String toString() {
		return "FileLocation [fileName=" + fileName + ", filePosition=" + filePosition + "]";
	}
}
