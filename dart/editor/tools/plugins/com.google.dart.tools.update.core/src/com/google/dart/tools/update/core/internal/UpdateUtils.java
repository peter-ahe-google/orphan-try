/*
 * Copyright (c) 2012, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.tools.update.core.internal;

import com.google.dart.tools.update.core.Revision;
import com.google.dart.tools.update.core.UpdateCore;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.util.Util;
import org.eclipse.swt.internal.Library;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

/**
 * Update utilities.
 */
@SuppressWarnings("restriction")
public class UpdateUtils {

  private static enum Arch {
    x32("ia32"),
    x64("x64"),
    UNKNOWN(null);

    private final String qualifier;

    Arch(String archQualifier) {
      this.qualifier = archQualifier;
    }
  }

  private static enum OS {
    WIN("windows"),
    OSX("macos"),
    LINUX("linux"),
    UNKNOWN(null);

    private final String qualifier;

    OS(String binaryQualifier) {
      this.qualifier = binaryQualifier;
    }
  }

  private static final Arch ARCH = getArch();
  private static final OS OS = getOS();

  private static int EXEC_MASK = 0111;

  // Set up proxy authentication support (dartbug.com/5455).
  static {
    try {
      Authenticator.setDefault(new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          if (getRequestorType() == RequestorType.PROXY) {
            String prot = getRequestingProtocol().toLowerCase();
            String host = System.getProperty(prot + ".proxyHost", "");
            String port = System.getProperty(prot + ".proxyPort", "80");
            String user = System.getProperty(prot + ".proxyUser", "");
            String password = System.getProperty(prot + ".proxyPassword", "");
            if (getRequestingHost().equalsIgnoreCase(host)) {
              if (Integer.parseInt(port) == getRequestingPort()) {
                return new PasswordAuthentication(user, password.toCharArray());
              }
            }
          }
          return null;
        }
      });
    } catch (SecurityException e) {
      UpdateCore.logError(e);
    }
  }

  // A sentinel file on windows to indicate that this binary is managed by an MSI installer
  private static final String INSTALLER_FILE = "README-WIN";

  /**
   * Copy the contents of one directory to another directory recursively.
   * 
   * @param fromDir the source
   * @param toDir the target
   * @param overwriteFilter a filter to determine if a given file should be overwritten in a copy
   * @param monitor a monitor for providing progress feedback
   * @throws IOException
   */
  public static void copyDirectory(File fromDir, File toDir, FileFilter overwriteFilter,
      IProgressMonitor monitor) throws IOException {
    for (File f : fromDir.listFiles()) {
      File toFile = new File(toDir, f.getName());
      if (f.isFile()) {
        if (!toFile.exists() || overwriteFilter.accept(toFile)) {
          copyFile(f, toFile, monitor);
        }
      } else {
        if (!toFile.isDirectory()) {
          toFile.delete();
        }
        if (!toFile.exists()) {
          toFile.mkdir();
        }

        copyDirectory(f, toFile, overwriteFilter, monitor);
      }
    }

  }

  /**
   * Copy a file from one place to another, providing progress along the way.
   */
  public static void copyFile(File fromFile, File toFile, IProgressMonitor monitor)
      throws IOException {

    byte[] data = new byte[4096];

    InputStream in = new FileInputStream(fromFile);

    toFile.delete();

    OutputStream out = new FileOutputStream(toFile);

    int count = in.read(data);

    while (count != -1) {
      out.write(data, 0, count);
      count = in.read(data);
    }

    in.close();
    out.close();

    toFile.setLastModified(fromFile.lastModified());
    if (fromFile.canExecute()) {
      toFile.setExecutable(true);
    }

    monitor.worked(1);

  }

  /**
   * Count the number of files in the given directory (for use in displaying progress).
   * 
   * @param file the file/dir of interest
   * @return the number of files
   */
  public static int countFiles(File file) {

    if (!file.isDirectory()) {
      return 1;
    }

    int count = 0;
    File[] files = file.listFiles();
    if (files != null) {
      for (File f : files) {
        count += countFiles(f);
      }
    }
    return count;
  }

  /**
   * Delete the given file. If the file is a directory, recurse and delete contents.
   * <p>
   * If the file does not exist, ignore.
   * 
   * @param file the file to delete
   * @param monitor the progress monitor
   */
  public static void delete(File file, IProgressMonitor monitor) {
    if (!file.exists()) {
      return;
    }
    if (file.isFile()) {
      file.delete();
      monitor.worked(1);
    } else {
      deleteDirectory(file, monitor);
    }
  }

  /**
   * Recurse and delete the given directory contents, providing progress along the way.
   * 
   * @param dir the directory to delete
   * @param monitor the progress monitor
   */
  public static void deleteDirectory(File dir, IProgressMonitor monitor) {
    if (dir == null || !dir.isDirectory()) {
      return;
    }

    File[] files = dir.listFiles();

    if (files == null) {
      return;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        // If it's symlinked, just delete the link - do not follow the linked dir.
        if (isLinkedFile(file)) {
          file.delete();
        } else {
          deleteDirectory(file, monitor);
        }
      } else {
        file.delete();
        monitor.worked(1);
      }
    }

    dir.delete();
    monitor.worked(1);
  }

  /**
   * Download a URL to a local file, notifying the given monitor along the way.
   */
  public static void downloadFile(URL downloadUrl, File destFile, String taskName,
      IProgressMonitor monitor) throws IOException {

    URLConnection connection = downloadUrl.openConnection();
    FileOutputStream out = new FileOutputStream(destFile);

    int length = connection.getContentLength();

    monitor.beginTask(taskName, length);
    copyStream(connection.getInputStream(), out, monitor, length);
    monitor.done();
  }

  /**
   * Ensure that the execute bit is set on the given files.
   * 
   * @param files the files that should be executable
   */
  public static void ensureExecutable(File... files) {
    if (files != null) {
      for (File file : files) {
        if (file != null && !file.canExecute()) {
          if (!makeExecutable(file)) {
            UpdateCore.logError("Could not make " + file.getAbsolutePath() + " executable");
          }
        }
      }
    }
  }

  /**
   * Get the file associating this binary with an installer, or <code>null</code> if there is none.
   */
  public static File getInstallerMarkerFile() {
    if (Util.isWindows()) {
      try {
        URL installLocation = Platform.getInstallLocation().getURL();
        return URIUtil.toPath(org.eclipse.core.runtime.URIUtil.toURI(installLocation)).append(
            INSTALLER_FILE).toFile();
      } catch (URISyntaxException e) {
        // Shouldn't happen and if it does, default to null
      }
    }
    return null;
  }

  /**
   * Build a platform-aware installer download URL for the given revision.
   * 
   * @param revision the revision
   * @return a download url
   * @throws MalformedURLException
   */
  public static URL getInstallerUrl(Revision revision) throws MalformedURLException {
    // darteditor-windows-ia32.zip => darteditor-installer-windows-ia32.msi
    String binaryName = getBinaryName();
    binaryName = binaryName.replace("darteditor-windows", "darteditor-installer-windows");
    binaryName = binaryName.subSequence(0, binaryName.length() - "zip".length()) + "msi";
    return new URL(UpdateCore.getUpdateUrl() + revision.toString() + "/" + binaryName);
  }

  /**
   * Parse the latest revision from the revision listing at the given url.
   * 
   * @param url the url to check
   * @return the latest revision, or <code>null</code> if none is found
   * @throws IOException if an exception occurred in retrieving the revision
   */
  public static Revision getLatestRevision(String url) throws IOException {
    List<Revision> revisions = parseRevisions(url);
    if (revisions.isEmpty()) {
      return null;
    }
    Collections.sort(revisions);
//    //TODO (pquitslund): for testing continuous, roll back one rev
//    return revisions.get(revisions.size() - 2);
    return revisions.get(revisions.size() - 1);
  }

  /**
   * Convert the given revision to a path where it should be staged in the editor's updates/
   * directory in the local filesystem.
   * 
   * @param revision the revision
   * @return the local file system path
   */
  public static IPath getPath(Revision revision) {
    IPath updateDirPath = UpdateCore.getUpdateDirPath();
    return updateDirPath.append(revision.toString()).addFileExtension("zip");
  }

  /**
   * Get the update directory.
   * 
   * @return the update directory
   */
  public static File getUpdateDir() {
    IPath updateDirPath = UpdateCore.getUpdateDirPath();

    File updateDir = updateDirPath.toFile();
    if (!updateDir.exists()) {
      updateDir.mkdirs();
    }
    return updateDir;
  }

  /**
   * Get the target update install directory.
   * 
   * @return the install directory
   */
  public static File getUpdateInstallDir() {

    if (UpdateCore.DEBUGGING_IN_RUNTIME_WS) {
      //TODO (pquitslund): for local testing
      return new File(getUpdateTempDir().getParentFile(), "install");
    }
    return getUpdateDir().getParentFile();

  }

  /**
   * Get the temporary update data directory.
   * 
   * @return the temporary update data directory
   */
  public static File getUpdateTempDir() {
    File updateDir = getUpdateDir();
    File tmpDir = new File(updateDir, "tmp");
    if (!tmpDir.isDirectory()) {
      tmpDir.delete();
      tmpDir = new File(updateDir, "tmp");
    }
    if (!tmpDir.exists()) {
      tmpDir.mkdir();
    }
    return tmpDir;
  }

  /**
   * Build a platform-aware download URL for the given revision.
   * 
   * @param revision the revision
   * @return a download url
   * @throws MalformedURLException
   */
  public static URL getUrl(Revision revision) throws MalformedURLException {
    return new URL(UpdateCore.getUpdateUrl() + revision.toString() + "/" + getBinaryName());
  }

  /**
   * Test for the presence of an installer.
   */
  public static boolean isInstallerPresent() {
    File installer = getInstallerMarkerFile();
    return installer != null && installer.exists();
  }

  /**
   * Check if the given zip file is valid.
   * 
   * @param zip the zip to check
   * @return <code>true</code> if it's valid, <code>false</code> otherwise.
   */
  public static boolean isZipValid(final File zip) {
    java.util.zip.ZipFile zipfile = null;
    try {
      zipfile = new java.util.zip.ZipFile(zip);
      return true;
    } catch (ZipException e) {
      return false;
    } catch (IOException e) {
      return false;
    } finally {
      try {
        if (zipfile != null) {
          zipfile.close();
          zipfile = null;
        }
      } catch (IOException e) {
      }
    }
  }

  /**
   * Parse the revision number from a JSON string.
   * <p>
   * Sample payload:
   * </p>
   * 
   * <pre>
   * { 
   *   "revision" : "9826", 
   *   "version"  : "0.0.1_v2012070961811", 
   *   "date"     : "2012-07-09" 
   * }
   * </pre>
   * 
   * @param versionJSON the json
   * @return a revision number or <code>null</code> if none can be found
   * @throws IOException
   */
  public static String parseRevisionNumberFromJSON(String versionJSON) throws IOException {
    try {
      JSONObject obj = new JSONObject(versionJSON);
      return obj.optString("revision", null);
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  /**
   * Parse the latest revision from the VERSION file at the given url.
   * 
   * @param url the url to check
   * @return the latest revision, or <code>null</code> if none is found
   * @throws IOException if an exception occurred in retrieving the revision
   */
  public static Revision parseVersionFile(String url) throws IOException {

    String versionFileContents = readUrlStream(url);

    //TODO (pquitslund): temporary check for "old" numeric format, 
    //to be removed once the JSON format is final
    String revisionString = versionFileContents.trim().matches("-?\\d+(.\\d+)?")
        ? versionFileContents : parseRevisionNumberFromJSON(versionFileContents);

    return Revision.forValue(revisionString);
  }

  /**
   * Read, as a string, the stream at the given url string.
   */
  public static String readUrlStream(String urlString) throws MalformedURLException, IOException {
    URL url = new URL(urlString);
    InputStream stream = url.openStream();
    return toString(stream);
  }

  /**
   * Unzip a zip file, notifying the given monitor along the way.
   */
  public static void unzip(File zipFile, File destination, String taskName, IProgressMonitor monitor)
      throws IOException {

    ZipFile zip = new ZipFile(zipFile);

    //TODO (pquitslund): add real progress units
    if (monitor != null) {
      monitor.beginTask(taskName, 1);
    }

    try {
      Enumeration<ZipArchiveEntry> e = zip.getEntries();

      while (e.hasMoreElements()) {
        ZipArchiveEntry entry = e.nextElement();
        File file = new File(destination, entry.getName());
        if (entry.isDirectory()) {
          file.mkdirs();
        } else {
          InputStream is = zip.getInputStream(entry);

          File parent = file.getParentFile();
          if (parent != null && parent.exists() == false) {
            parent.mkdirs();
          }

          FileOutputStream os = new FileOutputStream(file);
          try {
            IOUtils.copy(is, os);
          } finally {
            os.close();
            is.close();
          }
          file.setLastModified(entry.getTime());

          int mode = entry.getUnixMode();

          if ((mode & EXEC_MASK) != 0) {
            if (!file.setExecutable(true)) {
              UpdateCore.logError("Unable to set exec bit on: " + file.getPath());
            }
          }
        }
      }
    } finally {
      ZipFile.closeQuietly(zip);
    }

    //TODO (pquitslund): fix progress units
    if (monitor != null) {
      monitor.worked(1);
      monitor.done();
    }
  }

  private static void copyStream(InputStream in, FileOutputStream out, IProgressMonitor monitor,
      int length) throws IOException {
    byte[] data = new byte[4096];

    try {
      int count = in.read(data);
      while (count != -1) {
        if (monitor.isCanceled()) {
          throw new IOException("job cancelled");
        }
        out.write(data, 0, count);
        if (length != -1) {
          monitor.worked(count);
        }
        count = in.read(data);
      }
    } finally {
      in.close();
      out.close();
    }
  }

  private static Arch getArch() {
    try {
      return is64bitSWT() ? Arch.x64 : Arch.x32;
    } catch (Exception e) {
      UpdateCore.logError(e);
      return Arch.UNKNOWN;
    }
  }

  private static String getBinaryName() {
    return "editor/darteditor-" + OS.qualifier + "-" + ARCH.qualifier + ".zip";
  }

  @SuppressWarnings("static-access")
  private static OS getOS() {
    if (Util.isMac()) {
      return OS.OSX;
    }
    if (Util.isLinux()) {
      return OS.LINUX;
    }
    if (Util.isWindows()) {
      return OS.WIN;
    }
    return OS.UNKNOWN;
  }

  private static boolean is64bitSWT() throws Exception {
    Class<Library> swtLibraryClass = Library.class;
    Field is64 = swtLibraryClass.getDeclaredField("IS_64");
    is64.setAccessible(true);
    return is64.getBoolean(swtLibraryClass);
  }

  private static boolean isLinkedFile(File file) {
    try {
      IFileStore fileStore = EFS.getStore(file.toURI());

      IFileInfo info = fileStore.fetchInfo();

      return info.getAttribute(EFS.ATTRIBUTE_SYMLINK);
    } catch (CoreException ce) {
      return false;
    }
  }

  private static boolean isNumeric(String str) {
    for (char c : str.toCharArray()) {
      if (!Character.isDigit(c)) {
        return false;
      }
    }
    return true;
  }

  private static boolean makeExecutable(File file) {

    UpdateCore.logInfo("Setting execute bit for " + file.getAbsolutePath());

    // First try and set executable for all users.
    if (file.setExecutable(true, false)) {
      // success
      return true;
    }

    // Then try only for the current user.
    return file.setExecutable(true, true);
  }

  private static List<Revision> parseRevisions(String urlString) throws IOException {

    String str = readUrlStream(urlString);

    Pattern linkPattern = Pattern.compile(
        "<a[^>]+href=[\"']?([\"'>]+)[\"']?[^>]*>(.+?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    Matcher matcher = linkPattern.matcher(str);
    ArrayList<Revision> revisions = new ArrayList<Revision>();
    while (matcher.find()) {
      if (matcher.group(0).contains("dart-editor-archive")) {
        String revision = matcher.group(2);
        //drop trailing slash
        revision = revision.replace("/", "");
        //drop symbolic links (like "latest")
        if (isNumeric(revision)) {
          revisions.add(Revision.forValue(revision));
        }
      }
    }

    return revisions;
  }

  private static String toString(InputStream is) throws IOException {
    final char[] buffer = new char[0x10000];
    StringBuilder out = new StringBuilder();
    Reader in = new InputStreamReader(is, "UTF-8");
    int read;
    do {
      read = in.read(buffer, 0, buffer.length);
      if (read > 0) {
        out.append(buffer, 0, read);
      }
    } while (read >= 0);
    return out.toString();
  }
}
