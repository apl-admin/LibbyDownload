package com.apl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.net.ssl.HttpsURLConnection;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class LibbyDownload {
	private static String app = "Libby Download";
	private static String bookName = "Unknown Book";
	private static boolean bypassCache = false;
	private static boolean chapters = false;
	private static File coverArtfile;
	private static String cueFileName = null;
	private static Map<String, List<Cue>> cueMap = new TreeMap<>();
	private static boolean debug = false;
	private static final int DEFAULT_BUFFER_SIZE = 8192;
	private static boolean deleteFiles = false;
	private static String ffmpegLoc = null;
	private static List<File> fileList = new ArrayList<>();
	private static Set<String> fileNameSet = new TreeSet<>();
	private static String harFileName = "";
	private static Map<Object, Object> keyValueMap = new TreeMap<>();
	private static String outputFolder = ".";
	private static boolean processCueFile = false;
	private static List<Request> requestList = new ArrayList<>();
	private static String ver = "3.2";

	private static String formatSeconds(int totSec) {
		int min = totSec / 60;
		int sec = totSec - (min * 60);

		return min + ":" + String.format("%02d", sec) + ":00";
	}

	public static void main(String[] args) throws IOException, JSONException {
		System.out.println(app + " v" + ver);

		int i = 0;
		String arg;
		if (args.length == 0) {
			System.out.println(
					"Syntax:\njava -jar LibbyDownload.jar -book \"Name Of Book Files\" -har \"HAR File to Process\" -out \"Folder To Put Files In\" [-d -bc -c]");
			System.out.println(
					"\t-d gives you debug info, -bc tells the program to bypass files that came from cache because experience shows they don't download properly, and -c tells it to repackage the downloaded files into chapters instead of how Libby stored them");
		}

		while (i < args.length) {
			arg = args[i++];

			if (arg.equals("-book")) {
				if (i < args.length) {
					bookName = args[i++];
				}
			} else if (arg.equals("-har")) {
				if (i < args.length) {
					harFileName = args[i++];
				}
			} else if (arg.equals("-out")) {
				if (i < args.length) {
					outputFolder = args[i++];
				}
			} else if (arg.equals("-ffmpeg")) {
				if (i < args.length) {
					ffmpegLoc = args[i++];
				}
			} else if (arg.equals("-cue")) {
				if (i < args.length) {
					cueFileName = args[i++];
					processCueFile = true;
					chapters = true;

				}
			} else if (arg.equals("-d")) {
				debug = true;
			} else if (arg.equals("-bc")) {
				bypassCache = true;
			} else if (arg.equals("-c")) {
				chapters = true;
			} else if (arg.equals("-df")) {
				deleteFiles = true;
			}
		}

		coverArtfile = new File(outputFolder + File.separator + "coverArt.jpg");

		if (!processCueFile) {
			if (harFileName.isEmpty()) {
				System.err.println("Must Provide -har parameter before continuing");
			}

			File harFile = new File(harFileName);
			if (!harFile.exists()) {
				System.err.print("Har file specified '" + harFileName
						+ "' does not exist, did you include the file's extension?");
				System.exit(1);
			}
			System.out.println("Processing Har File: " + harFileName);
			String content = new String(Files.readAllBytes(Paths.get(harFileName)));
			try {
				JSONObject json = new JSONObject(content);

				if (json != null) {
					if (json.has("log")) {
						json = json.getJSONObject("log");
						if (json.has("entries")) {
							JSONArray entries = json.getJSONArray("entries");
							for (Object entryObject : entries) {
								if (entryObject instanceof JSONObject) {
									JSONObject entryJson = (JSONObject) entryObject;
									// System.out.println("Json: " + entryJson);
									processHtml(entryJson);
									processJson(entryJson, "role", "Author", "name", null);
									processJson(entryJson, "title");
									processJson(entryJson, "seriesName");
									processJson(entryJson, "readingOrder");
									processJson(entryJson, "sortTitle");
									processJson(entryJson, "publishDateText");
									processJson(entryJson, "fullDescription");
									processJson(entryJson, "subtitle");
									processJson(entryJson, "subtitle");
									processJson(entryJson, "publisher", "name");
									processJson(entryJson, "cover150Wide", "href");
									JSONObject requestJson = null, responseJson = null;
									String resourceType = null;
									if (entryJson.has("_resourceType")) {
										resourceType = entryJson.getString("_resourceType");
									}
									if (resourceType != null && resourceType.equals("media")) {
										if (entryJson.has("request")) {
											requestJson = entryJson.getJSONObject("request");
										}
										if (entryJson.has("response")) {
											responseJson = entryJson.getJSONObject("response");
										}
										if (requestJson != null && responseJson != null) {
											String url = null;
											int responseCode = -1;

											if (requestJson.has("url")) {
												url = requestJson.getString("url");
											}
											if (responseJson.has("status")) {
												responseCode = responseJson.getInt("status");
											}
											if (url != null && responseCode == 302) {
												if (url.contains("cachefly")) {
													continue;
												}
												Request request = new Request();
												request.setUrl(url);

												if (entryJson.has("_fromCache")) {
													request.setFromCache(true);
												}

												if (!requestList.contains(request)
														&& !(bypassCache && request.isFromCache())) {
													if (debug) {
														System.out.println("New Request URL/MimeType: " + request);
													}
													requestList.add(request);
													if (requestJson.has("headers")) {
														JSONArray headersArray = requestJson.getJSONArray("headers");
														if (headersArray != null && headersArray instanceof JSONArray) {
															for (Object headerObject : headersArray) {
																if (headerObject != null
																		&& headerObject instanceof JSONObject) {
																	JSONObject headerJson = (JSONObject) headerObject;
																	if (headerJson.has("name")
																			&& headerJson.has("value")) {
																		String headerName = headerJson
																				.getString("name");
																		String headerValue = headerJson
																				.getString("value");
																		if (headerName != null && headerValue != null
																				&& !headerName.startsWith(":")) {
																			request.addHeader(headerName, headerValue);
																		}
																	}
																}
															}
														}
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
				System.out.println("Har File Processed");
			} catch (Exception e) {
				System.out.println("Unable to parse HAR file: " + e.getMessage());
				System.out.println("HAR contents: " + content);
				e.printStackTrace();
				System.exit(1);
			}

			if (bookName.equals("Unknown Book") && keyValueMap.containsKey("title")) {
				bookName = replaceInvalid(keyValueMap.get("title").toString());
			}

			if (cueMap != null && cueMap.size() > 0) {
				System.out.println("Found and processed Table of Contents, creating CUE file");
				StringBuilder sb = new StringBuilder();
				int track = 1;
				for (String file : cueMap.keySet()) {
					sb.append("FILE \"" + bookName + file + "\" MP3" + System.lineSeparator());
					List<Cue> cueList = cueMap.get(file);
					for (Cue cue : cueList) {
						cue.setTrack(track);
						sb.append("TRACK " + track + " AUDIO" + System.lineSeparator());
						sb.append("  TITLE \"" + cue.getTitle() + "\"" + System.lineSeparator());
						sb.append("  INDEX 01 " + formatSeconds(cue.getOffset()) + System.lineSeparator());
						track++;
					}
				}
				BufferedWriter bw = null;
				try {
					File file = new File(outputFolder + File.separator + bookName + ".cue");

					FileWriter fw = new FileWriter(file);
					bw = new BufferedWriter(fw);
					bw.write(sb.toString());
					System.out.println("CUE file Created: " + file);

				} catch (IOException ioe) {
					ioe.printStackTrace();
				} finally {
					try {
						if (bw != null) {
							bw.close();
						}
					} catch (Exception e) {
						System.err.println("Error in closing the BufferedWriter" + e);
					}
				}
			} else {
				System.out.println("Unable to find Table of Contents, cannot create CUE file");
			}

			System.out.println(requestList.size() + " files found to download");

			if (requestList.size() > 0) {
				for (Request request : requestList) {
					processRequest(request);
				}
			}
		}

		if (ffmpegLoc != null) {
			File ffmpegFile = new File(ffmpegLoc);
			if (!ffmpegFile.exists()) {
				System.err.print("Location specified for FFMPEG is invalid, not processing chapters: " + ffmpegLoc);
				chapters = false;
				ffmpegLoc = null;
			}
		}

		if (chapters == true && ffmpegLoc == null) {
			System.err.println(
					"Chapters processing was specified, but location of ffmpeg was not specified, not processing chapters");
			chapters = false;
		}

		// if (coverArtfile.exists()) {
		// coverArtfile.delete();
		// }

		if (processCueFile && cueMap.size() == 0) {
			File cueFile = new File(cueFileName);
			if (cueFile == null || !cueFile.exists()) {
				System.err.println("Cue file specified, but file doesn't exist, not processing");
				processCueFile = false;
				chapters = false;
			}
			BufferedReader reader;

			try {
				reader = new BufferedReader(new FileReader(cueFileName));
				String line;

				List<Cue> cueList = null;
				String title = null;
				String index = null;
				int track = -1;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					// System.out.println("Cue Line: " + line);
					if (line.startsWith("FILE")) {
						String fileName = line.substring(6, line.lastIndexOf("\""));

						if (bookName.equals("Unknown Book")) {
							bookName = fileName.replaceAll("-Part.*", "");
						}
						File file = new File(outputFolder + File.separator + fileName);
						if (file.exists()) {
							fileList.add(file);
							fileNameSet.add(file.getAbsolutePath());
						}

						// System.out.println("File Name: " + fileName);
						cueList = cueMap.get(fileName);
						if (cueList == null) {
							cueList = new ArrayList<>();
							cueMap.put(fileName, cueList);
						}

					} else if (line.startsWith("TRACK")) {
						track = Integer.valueOf(line.substring(6, line.lastIndexOf(" ")));
					} else if (line.startsWith("TITLE")) {
						title = line.substring(7, line.lastIndexOf("\""));
						// System.out.println("Title: " + title);
					} else if (line.startsWith("INDEX")) {
						index = line.substring(9);
						// System.out.println("Index: " + index);
						int min = Integer.valueOf(index.substring(0, index.indexOf(":")));
						int sec = Integer.valueOf(index.substring(index.indexOf(":") + 1, index.lastIndexOf(":")));
						// System.out.println("Min: " + min);
						// System.out.println("Sec: " + sec);
						index = String.valueOf(((min * 60) + sec));
						// System.out.println("Secs: " + index);
					}
					if (cueList != null && title != null && index != null && track >= 0) {
						Cue cue = new Cue(title, index);
						cue.setTrack(track);
						cueList.add(cue);
						title = null;
						index = null;
						track = -1;
					}
				}

				reader.close();
			} catch (IOException e) {
				System.err.println("Error processing Cue file: " + e.getMessage());
				e.printStackTrace();
			}
		}

		if (chapters && cueMap.size() > 0) {
			int maxTrack = 0;
			if (chapters && cueMap != null && cueMap.size() > 0) {
				for (List<Cue> cueList : cueMap.values()) {
					for (Cue cue : cueList) {
						if (cue.getTrack() > maxTrack) {
							maxTrack = cue.getTrack();
						}
					}
				}
			}
			int maxTrackLength = String.valueOf(maxTrack).length();
			if (chapters && cueMap != null && cueMap.size() > 0) {
				System.out.println("Processing Chapter Information");
				// setup chapter list
				List<Chapter> chapterList = new ArrayList<>();
				for (String cueKey : cueMap.keySet()) {
					List<Cue> cueList = cueMap.get(cueKey);
					for (Cue cue : cueList) {
						StringBuilder sb = new StringBuilder();
						sb.append(bookName);
						sb.append("-");
						sb.append(String.format("%0" + maxTrackLength + "d", cue.getTrack()));
						sb.append("-");
						sb.append(cue.getTitle());

						Chapter chapter = new Chapter(sb.toString());
						chapterList.add(chapter);

						for (String fileName : fileNameSet) {
							if (fileName.contains(cueKey)) {
								chapter.addFile(fileName);
								chapter.addFileLocation("start", fileName, cue.getOffset());
								break;
							}
						}
					}
				}

				// process chapter file data
				Chapter pChapter = null;
				for (Chapter cChapter : chapterList) {
					if (pChapter != null) {
						if (pChapter.getLastFile().equals(cChapter.getFirstFile())) {
							pChapter.addFileLocation("end", cChapter.getFirstFile(), cChapter.getStartLoc() - 1);
						} else {
							boolean started = false;
							for (String fileName : fileNameSet) {
								if (fileName.equals(pChapter.getFirstFile())) {
									started = true;
									continue;
								}
								if (started && !pChapter.getFileList().contains(fileName)) {
									pChapter.addFile(fileName);
								}
								if (started && cChapter.getFirstFile().equals(fileName)) {
									if (cChapter.getStartLoc() > 0) {
										pChapter.addFileLocation("end", cChapter.getFirstFile(),
												cChapter.getStartLoc() - 1);
									} else {
										pChapter.getFileList()
												.remove(cChapter.getFileLocMap().get("start").getFileName());
										pChapter.addFileLocation("end", pChapter.getLastFile(), -1);
									}
									break;
								}

							}
						}
					}
					pChapter = cChapter;
				}

				// handle finished chapter info
				boolean errorOccurred = false;
				for (Chapter cChapter : chapterList) {
					List<String> commandList = new ArrayList<String>();
					commandList.add(ffmpegLoc);
					commandList.add("-y");

					System.out.println("Creating Track file: " + cChapter.getTitle() + ".mp3");
					List<String> fileNameList = cChapter.getFileList();
					Map<String, FileLocation> fileLocMap = cChapter.getFileLocMap();
					if (!fileLocMap.containsKey("start")) {
						System.out.println("File Loc Map: " + fileLocMap);
						System.exit(1);
					}
					FileLocation startFileLoc = fileLocMap.get("start");
					if (startFileLoc == null) {
						startFileLoc = new FileLocation("Unknown", -10);
					}
					FileLocation endFileLoc = fileLocMap.get("end");
					if (endFileLoc == null) {
						endFileLoc = new FileLocation(startFileLoc.getFileName(), -10);
					}

					commandList.add("-ss");
					commandList.add(String.valueOf(startFileLoc.getFilePosition()));
					if (startFileLoc.getFileName().equals(endFileLoc.getFileName())) {
						if (endFileLoc.getFilePosition() >= 0) {
							commandList.add("-to");
							commandList.add(String.valueOf(endFileLoc.getFilePosition()));
						}
						commandList.add("-i");
						commandList.add(endFileLoc.getFileName());
					} else {
						commandList.add("-i");
						commandList.add(startFileLoc.getFileName());
					}

					for (String fileName : fileNameList) {
						if (!fileName.equals(startFileLoc.getFileName())
								&& !fileName.equals(endFileLoc.getFileName())) {
							commandList.add("-i");
							commandList.add(fileName);
						}

						// System.out.println("\tFile: " + fileName);
					}
					if (!startFileLoc.getFileName().equals(endFileLoc.getFileName())) {
						if (endFileLoc.getFilePosition() >= 0) {
							commandList.add("-to");
							commandList.add(String.valueOf(endFileLoc.getFilePosition()));
						}
						commandList.add("-i");
						commandList.add(endFileLoc.getFileName());
					}

					// System.out.println("\tLoc: " + startFileLoc);
					// System.out.println("\tLoc: " + endFileLoc);
					commandList.add("-lavfi");
					commandList.add("concat=n=" + cChapter.getFileList().size() + ":a=1");
					commandList.add("-acodec");
					commandList.add("mp3");
					commandList.add(outputFolder + File.separator + cChapter.getTitle() + ".mp3");

					if (debug) {
						for (String s : commandList) {
							System.out.print("\"" + s + "\" ");
						}
						System.out.println();
					}

					ProcessBuilder processBuilder = new ProcessBuilder(commandList);
					processBuilder.redirectErrorStream(true);
					Process process = processBuilder.start();
					StringBuilder sb = new StringBuilder();
					if (process != null) {
						BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
						String line = null;
						while (process.isAlive()) {
							try {
								line = stdOut.readLine();
							} catch (IOException e) {
								System.err.println("Error reading line: " + e.getMessage());
								line = null;
							}
							if (line != null) {
								line = line.trim();
								sb.append(line + System.lineSeparator());
								// System.out.println(line);
							}
						}
					}
					if (process.exitValue() > 0) {
						System.err.println("Error occurred during Chapter creation");
						System.err.println(sb.toString());
						System.exit(1);
					}
				}
				if (deleteFiles && !errorOccurred) {
					System.out.println("Removing original 'Part' files");
					for (File file : fileList) {
						if (file.exists() && file.getName().contains("-Part")) {
							file.delete();
						}
					}
				}
			}
		}

		System.out.println("Program Complete");
	}

	private static void processHtml(JSONObject json) {
		for (String curKey : json.keySet()) {
			if (cueMap.size() > 0) {
				break;
			}
			Object curValue = json.get(curKey);
			// System.out.println("curKey: " + curKey + " class: " + curValue.getClass() +
			// "curValue: " + curValue);
			// System.out.println(curValue.getClass() + ": " + curKey + "/" + curValue);
			if (curValue instanceof JSONObject) {
				processHtml((JSONObject) curValue);
			} else if (curValue instanceof String) {
				if (curValue.toString().startsWith("<!")) {
					// System.out.println("HTML?: " + curValue);
					Document document = Jsoup.parse((String) curValue);
					Elements elements = document.body().select("*");

					for (Element element : elements) {
						if (element.nodeName().equals("script") && element.id().equals("BIFOCAL-data")) {
							String data = element.data();
							String[] tokens = data.split("\\n");
							for (String token : tokens) {
								if (token.indexOf("=") > 0) {
									token = token.substring(token.indexOf("=") + 1).trim();
									if (token.startsWith("{")) {
										try {
											JSONObject scriptJson = new JSONObject(token);
											if (scriptJson.has("creator")) {
												JSONArray creatorArray = scriptJson.getJSONArray("creator");
												for (Object o : creatorArray) {
													JSONObject creatorJson = (JSONObject) o;
													String name = creatorJson.getString("name");
													String role = creatorJson.getString("role");

													if (name != null && !name.isEmpty() && role.equals("author")) {
														System.out.println("Setting Author to " + name);
														keyValueMap.put("Author", name);
													}
													if (name != null && !name.isEmpty() && role.equals("narrator")) {
														System.out.println("Setting Narrator to " + name);
														keyValueMap.put("Narrator", name);
													}
												}
											}
											if (scriptJson.has("nav")) {
												JSONObject navJson = scriptJson.getJSONObject("nav");
												if (navJson.has("toc")) {
													JSONArray tocArray = navJson.getJSONArray("toc");
													for (Object oJ : tocArray) {
														JSONObject tocJson = (JSONObject) oJ;
														String file = null;
														String title = tocJson.getString("title")
																.replaceAll("[^ a-zA-Z0-9-\\.,]", "");
														String path = tocJson.getString("path");
														String offset = "0";
														if (path != null) {
															if (path.contains("-")) {
																file = path.substring(path.lastIndexOf("-"));
															}
															if (path.contains("#")) {
																file = file.substring(0, file.indexOf("#"));
																offset = path.substring(path.indexOf("#") + 1);
															}
														}
														if (file != null) {
															List<Cue> cueList = cueMap.get(file);
															if (cueList == null) {
																cueList = new ArrayList<>();
																cueMap.put(file, cueList);
															}
															Cue newCue = new Cue(title, offset);
															if (debug) {
																System.out.println("TOC Title: " + title + " File: "
																		+ file + " Offset: " + offset);
															}
															cueList.add(newCue);
														} else {
															System.err.println(
																	"Error retrieving File name from Path: " + path);
														}
													}
												}
											}
											if (scriptJson.has("description")) {
												String description = scriptJson.getJSONObject("description")
														.getString("full");
												System.out.println("Setting Description to " + description);
												keyValueMap.put("Description", description);
											}

											if (scriptJson.has("title")) {
												JSONObject titleJson = scriptJson.getJSONObject("title");
												String s;
												if (titleJson.has("main")) {
													s = titleJson.getString("main");
													if (s != null && !s.isEmpty()) {
														System.out.println("Setting Title to " + s);
														keyValueMap.put("title", s);
													}
												}
												if (titleJson.has("subtitle")) {
													s = titleJson.getString("subtitle");
													if (s != null && !s.isEmpty()) {
														System.out.println("Setting Subtitle to " + s);
														keyValueMap.put("subtitle", s);
													}
												}
												if (titleJson.has("collection")) {
													s = titleJson.getString("collection");
													if (s != null && !s.isEmpty()) {
														System.out.println("Setting Series Name to " + s);
														keyValueMap.put("seriesName", s);
													}
												}

											}

										} catch (Exception e) {
											System.out.println("Exception: " + e.getMessage());
											e.printStackTrace();
										}
									}
								}
							}
							// System.out.println("Element: " + element.nodeName() + " | " + element);
						}
					}
				} else {
					String s = curValue.toString();
					if (s.length() > 30 && !s.startsWith("http") && !s.startsWith("{") && !s.startsWith("SPARK")) {
						// System.out.println("Not HTML?: " + curValue.toString());
					}
				}
			}

			// System.out.println("Key/Value: " + curKey + "/" + json.get(curKey));
		}

	}

	private static void processJson(JSONObject json, String key) {
		processJson(json, key, null, null, null);
	}

	private static void processJson(JSONObject json, String key, String subKey) {
		processJson(json, key, null, null, subKey);
	}

	private static void processJson(JSONObject json, String key, String keyValue, String value, String subKey) {
		// System.out.println("Start JSON Processing");
		if (keyValue == null) {
			keyValue = "";
		}
		if (value == null) {
			value = "";
		}
		if (json.has(key) && json.has(value) && json.get(key).equals(keyValue)) {
			if (!keyValueMap.containsKey(json.get(key))) {
				System.out.println("Setting " + json.get(key) + " to " + json.get(value));
				keyValueMap.put(json.get(key), json.get(value));
				return;
			}
		}
		if (json.has(key) && keyValue.isEmpty() && value.isEmpty() && subKey != null) {
			Object o = json.get(key);
			if (o instanceof JSONObject) {
				JSONObject sub = (JSONObject) o;
				if (sub.has(subKey)) {
					if (!keyValueMap.containsKey(key)) {
						System.out.println("Setting " + key + " to " + sub.get(subKey));
						keyValueMap.put(key, sub.get(subKey));
						return;
					}
				}
			}

		}
		if (json.has(key) && keyValue.isEmpty() && value.isEmpty()) {
			Object o = json.get(key);
			if (o instanceof String) {
				String s = (String) o;
				if (s.startsWith("{")) {
					processJson(new JSONObject(s), key, keyValue, value, subKey);
					return;
				}
				if (!keyValueMap.containsKey(key)) {
					System.out.println("Setting " + key + " to " + json.get(key));
					keyValueMap.put(key, json.get(key));
					// System.out.println(json);
					// System.exit(1);
					return;
				}
			}
		}

		for (String curKey : json.keySet()) {
			Object curValue = json.get(curKey);
			// System.out.println(curValue.getClass() + ": " + curKey + "/" + curValue);
			if (curValue instanceof String) {
				if (curValue.toString().startsWith("{")) {
					try {
						processJson(new JSONObject(curValue.toString()), key, keyValue, value, subKey);
					} catch (Exception e) {
					}
				} else if (curValue.toString().startsWith("[")) {
					try {
						JSONArray jsonArray = new JSONArray(curValue.toString());
						processJsonArray(jsonArray, key, keyValue, value, subKey);
					} catch (Exception e) {
					}
				}
			} else if (curValue instanceof JSONObject) {
				processJson((JSONObject) curValue, key, keyValue, value, subKey);
			} else if (curValue instanceof JSONArray) {
				processJsonArray((JSONArray) curValue, key, keyValue, value, subKey);
			}

			// System.out.println("Key/Value: " + curKey + "/" + json.get(curKey));
		}
	}

	private static void processJsonArray(JSONArray array, String key, String keyValue, String value, String subKey) {
		// System.out.println("Start JSONArray Processing");
		for (Object o : array) {
			// System.out.println("Object: " + o);
			if (o instanceof JSONObject) {
				processJson((JSONObject) o, key, keyValue, value, subKey);
			} else if (o instanceof JSONArray) {
				processJsonArray((JSONArray) o, key, keyValue, value, subKey);
			} else if (o instanceof String) {
			} else if (o instanceof Integer) {
			} else if (o instanceof Long) {
			} else if (o instanceof org.json.JSONObject) {
			} else {
				System.out.println("Exception handling of some sort: " + o.getClass() + ": " + o);
			}
		}
	}

	private static void processRequest(Request request) {
		if (debug) {
			System.out.println("Downloading Audio: " + request.getUrl());
		}
		String outputFileName = replaceInvalid((bookName + "-" + request.getFileName()));
		File file = new File(outputFolder + File.separator + outputFileName);
		if (file.exists()) {
			System.out.println(file.getAbsolutePath() + " already exists, skipping download");
			fileList.add(file);
			fileNameSet.add(file.getAbsolutePath());
			return;
		}
		URL url;
		URLConnection webConn;
		InputStream is;
		try {
			url = new URL(request.getUrl());
			webConn = url.openConnection();
			((HttpURLConnection) webConn).setRequestMethod("GET");
			webConn.setDoOutput(true);
			webConn.setDoInput(true);
			HttpURLConnection.setFollowRedirects(true);
			HttpsURLConnection.setFollowRedirects(true);

			boolean doit = true;

			if (doit) {
				Map<String, String> headers = request.getHeaders();
				for (String key : headers.keySet()) {
					// System.out.println("Header: " + key + "/" + headers.get(key));
					webConn.setRequestProperty(key, headers.get(key));
				}
				webConn.connect();

				is = webConn.getInputStream();

				System.out.println("Saving file: " + file.getAbsolutePath());
				fileList.add(file);
				// write the input stream to the output file
				java.nio.file.Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			fileNameSet.add(file.getAbsolutePath());

		} catch (Exception e) {
			System.err.println("Error downloading file: " + e.getMessage());
			// e.printStackTrace();
			if (request.isFromCache()) {
				System.err.println("Har reports file was from Cache");
			}
			return;
		}

		// time to process the metadata
		try {
			if (file != null && file.exists()) {
//				Mp3File mp3file = new Mp3File(file);
//				ID3v2 id3v2Tag;
//				if (mp3file.hasId3v2Tag()) {
//					id3v2Tag = mp3file.getId3v2Tag();
//				} else {
//					// mp3 does not have an ID3v2 tag, let's create one..
//					id3v2Tag = new ID3v24Tag();
//					mp3file.setId3v2Tag(id3v2Tag);
//				}
//				ArrayList<ID3v2ChapterFrameData> chapterList = new ArrayList<>();
//				ID3v2ChapterFrameData chapterData = new ID3v2ChapterFrameData(false);
//				chapterData.setStartTime(0);
//				chapterData.setEndOffset(32);
//				id3v2Tag.setChapters(chapterList);
//				mp3file.
//				System.exit(0);

				AudioFile audioFile = AudioFileIO.read(file);
				Tag tag = audioFile.getTagOrCreateDefault();

				tag.setField(FieldKey.ALBUM, bookName);
				tag.setField(FieldKey.TITLE, bookName);
				tag.setField(FieldKey.TRACK_TOTAL, String.valueOf(requestList.size()));
				if (keyValueMap.containsKey("subtitle")) {
					tag.setField(FieldKey.SUBTITLE, keyValueMap.get("subtitle").toString() + " - " + bookName);
				}
				if (keyValueMap.containsKey("seriesName")) {
					tag.setField(FieldKey.MOVEMENT, keyValueMap.get("seriesName").toString());
				}
				if (keyValueMap.containsKey("sortTitle")) {
					tag.setField(FieldKey.TITLE_SORT, keyValueMap.get("sortTitle").toString());
				}
				if (keyValueMap.containsKey("publishDateText")) {
					String[] dateTokens = keyValueMap.get("publishDateText").toString().split("[-/]");
					String year = null;
					for (String token : dateTokens) {
						if (token.length() == 4) {
							year = token;
						}
					}
					if (year != null) {
						tag.setField(FieldKey.YEAR, year);
					}
				}
				// tag.setField(FieldKey.TRACK, String.valueOf(request.getFileNumber()));
				if (keyValueMap.containsKey("Author")) {
					tag.setField(FieldKey.ARTIST, keyValueMap.get("Author").toString());
					tag.setField(FieldKey.ALBUM_ARTIST, keyValueMap.get("Author").toString());
				}
				if (keyValueMap.containsKey("fullDescription")) {
					tag.setField(FieldKey.COMMENT, keyValueMap.get("fullDescription").toString());
				}
				if (keyValueMap.containsKey("cover150Wide")) {
					if (!coverArtfile.exists() && keyValueMap.containsKey("cover150Wide")) {
						try {
							url = new URL(keyValueMap.get("cover150Wide").toString());
							webConn = url.openConnection();
							((HttpURLConnection) webConn).setRequestMethod("GET");
							webConn.setDoOutput(true);
							webConn.setDoInput(true);
							HttpURLConnection.setFollowRedirects(true);
							HttpsURLConnection.setFollowRedirects(true);

							webConn.connect();

							is = webConn.getInputStream();

							System.out.println("Saving file: " + coverArtfile.getAbsolutePath());

							try (FileOutputStream outputStream = new FileOutputStream(coverArtfile, false)) {
								int read;
								byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
								while ((read = is.read(bytes)) != -1) {
									outputStream.write(bytes, 0, read);
								}
								outputStream.flush();
								outputStream.close();
							}
						} catch (Exception e) {
							System.err.println("Error downloading Cover Art: " + e.getMessage());
						}
					}
					Artwork artwork = ArtworkFactory.createArtworkFromFile(coverArtfile);

					tag.deleteArtworkField();
					tag.setField(artwork);
				}
				audioFile.setTag(tag);
				audioFile.commit();
			}
		} catch (Exception e) {
			System.err.println(
					"Exception trying to add metadata to file " + file.getAbsolutePath() + ": " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static String replaceInvalid(String s) {
		return s.replaceAll("[^ a-zA-Z0-9-\\.]", "");
	}
}
