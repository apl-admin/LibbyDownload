This app is a Java hack that allows you to borrow books with Libby and download the audio in MP3 Format. Essentially, the Java program analyzes the files accessed as you play the book at high speed, and then fetches those files and names them appropriately. These instructions are oriented towards Windows users.  If you have not already, download the program from  https://remedylegacy.com/Files/LibbyDownload-3.1.zip.  Then extract the files.

First, you need to have an account at https://libbyapp.com/shelf

Second, you need Java.  In Windows type "cmd" in the Search line or Run box (Windows Key-R) and hit Enter to open a command window.  To check if you have Java installed and it is in the path, type 'java -version' on the command line and hit Enter.  If you do not get info about Java, then you'll need to download and install it.  If you need to install Java, one source is https://www.java.com/en/download/.  Close this command window.

Once you can get to one of those audiobook URLs and Java is available and you have a book ready to listen to, then follow these steps to record your session. These instructions work using Google Chrome and Microsoft Edge, but other browsers that can record web activity to a HAR file may work.  Experiments with Safari seeem to be successful as well, but Firefox doesn't seem to work as well sometimes missing part of the first file, sometimes grabbing more files than needed

1 - Go to Libby and log in.
2 - Make your browser full screen if it is not already.
3 - Right Click on the browser window and choose 'Inspect' (or F12 works as well). This opens a panel within the browser window normally used to debug web pages when they are being developed.
4 - Go to the 'Network' tab of the panel that just opened.  Because the Inspect window is narrow if it opens on the right side, you may need to click the ">>" on the tab line in order to see the 'Network' tab choice.  Or you can click on the 3 dots menu and change the doc location to the bottom to see all of the tabs 
5 - Navigate to the page in Libby to get to the book you want to download.
6 - Clear the network traffic list (click the slashed zero icon) and ensure 'Disable Cache' is checked. Then do a Ctrl+F5 (some require Ctrl+Fn+F5) to re-load the page without cache to ensure you get all of the audio files in the book.  If you don't do this part, you might not get the first file from the book at all because it's already downloaded.
7 - Hit the 'Play' button.  Ensure that you hear audio start playing.
8 - Holding down the right arrow on your keyboard, the book should start jumping forward in 15 second increments.  Hold it down till you reach the end of the book.  This takes about 2 minutes for a 15 hour book.
9 - In the Inspect panel, click on the 'Down' arrow icon that has a text tooltip that says 'Export HAR...'.  If you do not see that icon, make the Inspect panel wider by dragging its left-hand boundary to the left.  Or right-click the text in the panel and select that choice.
10 - Save the HAR file anywhere (but remember where it is!).
11 - Open a command prompt as described above and use the "cd" command to change into the directory where the LibbyDownload jar file was placed. In Windows Explorer, you can right click on the jar file and select Properties, and then copy the path from there.
12 - Either run the program from command prompt as below, or drag and drop your har file onto the provided batch/shell file.

     java -jar LibbyDownload.jar -har "the file you just saved" -out "directory you want files to go to"

The only required parameter is -har and be sure to include the ".har" suffix in what you provide.  The output will default to the current directory if not specified.  Remember that if you are going to put spaces into any of these values, you need to have either a " (if running windows) or a ' (if running Linux) around your parameters.  The default book name is "Unknown Book"; the default output folder is the current folder.  If you specify a folder that doesn't exist, you will get an error.

Chapter Processing:
The default mode of the tool is to download the files as Libby has them, but there is an additional processing that can happen to re-package the files into chapters.  This is done using a tool named 'ffmpeg'.  This tool is readily availbale for any os you can conceive of on the internet, and comes OOTB with many Linux Distributions.  If you want to have the downloaded files re-processed into chapters, you can specify a few new parameters

     java -jar LibbyDownload.jar -har "the file you just saved" -out "directory you want files to go to" -c -ffmpeg "c:\temp\ffmpeg.exe" -del
	 
the -c tells the tool that you want to process chapters.
the -ffmpeg tells it where to find ffmpeg, if you are on Windows this will be the full path to the executable, including the .exe.  If on Linux/Mac, this'll be the path to the executable, typically without an extension
the -del is an optional Chapter processing function.  After it re-processes all of the chapters it can optionally delete the -Part files that were downloaded.  If not specified this will leave both sets of files in the output folder

Cue File Processing:
If the Har file contains a Table of Contents or TOC, it will create a .cue file in the output folder.  This cue file is a playlist of sorts that will tell something like VLC where the chapters are.  If you have a cue file generated, and decided not to use chapter processing while processing the har file, you have the option of post processing the cue file te generate the chapter files afterwards.  This is an alternate way to run the program.

     java -jar LibbyDownload.jar -cue "the cue file" -out "directory you want files to go to" -c -ffmpeg "c:\temp\ffmpeg.exe" -del

by specifying -cue, you are specifically requesting chapter processing, so -c is not necessary.  You still need to specify the location of the ffmpeg, and the optional -del to remove the part files after chapter processing


The program will read through the HAR file and look for any audio file that is referenced and will save that file using the book name and appending the part number.  The names of the output files are listed in the command window as they are found and processed.  

As your browser is downloading the book and playing the sound files, the Inspect window generates a log of everything the browser is doing.  The Network window shows the files accessed.  The LibbyDownload program loads that log of what happened when the book was "read" and looks for requests to a web server where the response is an audio file.  The program then repeats that request and gets the audio file that your browser already got.  It then saves that audio file to your hard drive.  Your browser requested the same file multiple times in smaller segments.  This program downloads the whole file at once, in the same order it was requested in the browser.  What you have at the end is all of the audio files, in the order of the book.  It names them properly so that you can play them from the beginning to end without issue.

TROUBLESHOOTING

If while processing your har you get a 403 error, that means that the URL your browser used to download the files is no longer authorized.  Re-Record your HAR and try again shortly after recording and you will likely have better luck.  Typically the recording should work for around 24 hours after being recorded.
You can add a -d to the prompt and it'll give you debug details as it processes the har file
If you are having problems with some of the files being cached and not downloadable, you can add -bc and the tool will not try to process those cached files, instead only processing the ones the browser actually downloaded

Release Notes:
1.0 - Initial Release
1.5 - Added in MP3 Tag collection and saving.  That way if you are putting these files anywhere that uses these tags it'll know the author, series, etc, this removes the need for the -book parameter previously specified.
1.6 - Fixed some file naming problems introduced in 1.5
1.7 - Changed some of how the metadata is stored to better conform with the new app I'm using 'AudioBookshelf'
1.8 - Changed around how I'm looking at the HAR file and what I'm looking for, streamlined the process and helped remove 403 errors
2.0 - Changed how the files are named to properly identify the part number from the URL that's utilized
2.1 - Added creation of a CUE file.  This is a standardized file that identifies chapters within files.  It can be used by players like VLC to properly manage chapters while listening to playback.  Also, special thanks to papadjeef for his work on creating a Mac/Linux script that works similarly to the batch file
3.0 - Added chapter processing via the cue file and ffmpeg
3.1 - Added cue processing separate from the har processing, allowing post-processing of chapters given the cue file generated during har processing
