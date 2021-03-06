package ch.fhnw.dlcopy;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.ProcessExecutor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingWorker;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Creates an ISO file from a running system
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class IsoCreator
        extends SwingWorker<Boolean, String>
        implements PropertyChangeListener {

    private static final Logger LOGGER
            = Logger.getLogger(IsoCreator.class.getName());
    private final static ProcessExecutor PROCESS_EXECUTOR
            = new ProcessExecutor();

    // xorriso output looks like this:
    // xorriso : UPDATE : Writing:    234234s  31.5%   fifo 23% buf 50% 12.7xD
    //
    // The first 31.5% is the progress value we are looking for. We are only
    // interested in the integer value (31 in the example above).
    private static final Pattern XORRISO_PATTERN
            = Pattern.compile(".* (.*)\\..*% .*%.*%.*");

    // mksquashfs output looks like this:
    // [==========           ]  43333/230033  18%
    private static final Pattern MKSQUASHFS_PATTERN
            = Pattern.compile("\\[.* (.*)/(.*) .*");

    private final DLCopyGUI dlCopyGUI;
    private final SystemSource systemSource;
    private final boolean onlyBootMedium;
    private final String tmpDirectory;
    private final DataPartitionMode dataPartitionMode;
    private final boolean showNotUsedDialog;
    private final boolean autoStartInstaller;
    private final String isoLabel;

    private enum Step {
        MKSQUASHFS, GENISOIMAGE
    }
    private Step step;
    private String isoPath;
    private LogindInhibit inhibit = null;

    /**
     * creates a new ISOCreator
     *
     * @param dlCopyGUI the DLCopy GUI
     * @param systemSource the system source
     * @param onlyBootMedium if only a boot medium should be created
     * @param tmpDirectory the path to a temporary directory
     * @param dataPartitionMode the data partition mode
     * @param showNotUsedDialog if the dialog that data partition is not in use
     * should be shown
     * @param autoStartInstaller if the installer should start automatically if
     * no datapartition is in use
     * @param isoLabel the label to use for the final ISO
     */
    public IsoCreator(DLCopyGUI dlCopyGUI, SystemSource systemSource,
            boolean onlyBootMedium, String tmpDirectory,
            DataPartitionMode dataPartitionMode,
            boolean showNotUsedDialog, boolean autoStartInstaller,
            String isoLabel) {
        this.dlCopyGUI = dlCopyGUI;
        this.systemSource = systemSource;
        this.onlyBootMedium = onlyBootMedium;
        this.tmpDirectory = tmpDirectory;
        this.dataPartitionMode = dataPartitionMode;
        this.showNotUsedDialog = showNotUsedDialog;
        this.autoStartInstaller = autoStartInstaller;
        this.isoLabel = isoLabel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        inhibit = new LogindInhibit("Creating ISO");

        try {
            dlCopyGUI.showIsoProgressMessage(
                    STRINGS.getString("Copying_Files"));

            // create new temporary directory in selected directory
            File tmpDirFile = LernstickFileTools.createTempDirectory(
                    new File(tmpDirectory), "Lernstick-ISO");
            String targetRootDirectory = tmpDirFile.getPath();
            String targetDirectory = targetRootDirectory + "/build";
            if (!new File(targetDirectory).mkdirs()) {
                throw new IOException("could not create build directory "
                        + targetDirectory);
            }

            // copy boot files
            if (systemSource.hasEfiPartition()) {
                // system with a separate boot partition
                CopyJob bootCopyJob = new CopyJob(
                        new Source[]{
                            systemSource.getEfiCopySource(),
                            systemSource.getSystemCopySourceBoot()
                        },
                        new String[]{targetDirectory});
                FileCopier fileCopier = new FileCopier();
                fileCopier.copy(bootCopyJob);
                systemSource.unmountTmpPartitions();

            } else {
                // legacy system without separate boot partition
                String copyScript = "#!/bin/sh" + '\n'
                        + "cd " + systemSource.getSystemPath() + '\n'
                        + "find -not -name filesystem*.squashfs | cpio -pvdum \""
                        + targetDirectory + "\"";
                PROCESS_EXECUTOR.executeScript(copyScript);
            }

            if (!onlyBootMedium) {
                createSquashFS(targetDirectory);
            }

            DLCopy.setDataPartitionMode(systemSource,
                    dataPartitionMode, targetDirectory);

            // syslinux -> isolinux
            final String SYSLINUX_DIR = targetDirectory + "/syslinux";
            if (new File(SYSLINUX_DIR).exists()) {
                final String ISOLINUX_DIR = targetDirectory + "/isolinux";
                DLCopy.moveFile(SYSLINUX_DIR, ISOLINUX_DIR);
                final String syslinuxBinPath
                        = ISOLINUX_DIR + "/syslinux.bin";
                File syslinuxBinFile = new File(syslinuxBinPath);
                if (syslinuxBinFile.exists()) {
                    DLCopy.moveFile(syslinuxBinPath,
                            ISOLINUX_DIR + "/isolinux.bin");
                }
                DLCopy.moveFile(ISOLINUX_DIR + "/syslinux.cfg",
                        ISOLINUX_DIR + "/isolinux.cfg");

                // replace "syslinux" with "isolinux" in some files
                Pattern pattern = Pattern.compile("syslinux");
                LernstickFileTools.replaceText(ISOLINUX_DIR + "/exithelp.cfg",
                        pattern, "isolinux");
                LernstickFileTools.replaceText(ISOLINUX_DIR + "/stdmenu.cfg",
                        pattern, "isolinux");
                LernstickFileTools.replaceText(ISOLINUX_DIR + "/isolinux.cfg",
                        pattern, "isolinux");
            }

            // update md5sum
            dlCopyGUI.showIsoProgressMessage(
                    STRINGS.getString("Updating_Checksums"));
            String md5header = "This file contains the list of md5 "
                    + "checksums of all files on this medium.\n"
                    + "\n"
                    + "You can verify them automatically with the "
                    + "'integrity-check' boot parameter,\n"
                    + "or, manually with: 'md5sum -c md5sum.txt'.";
            try (FileWriter fileWriter
                    = new FileWriter(targetDirectory + "/md5sum.txt")) {
                fileWriter.write(md5header);
            }
            String md5Script = "cd \"" + targetDirectory + "\"\n"
                    + "find . -type f \\! -path './isolinux/isolinux.bin' "
                    + "\\! -path './boot/grub/stage2_eltorito' -print0 | "
                    + "sort -z | xargs -0 md5sum >> md5sum.txt";
            PROCESS_EXECUTOR.executeScript(md5Script);

            // create new iso image
            isoPath = targetRootDirectory + "/Lernstick.iso";
            step = Step.GENISOIMAGE;
            dlCopyGUI.showIsoProgressMessage(
                    STRINGS.getString("Creating_Image"));
            PROCESS_EXECUTOR.addPropertyChangeListener(this);

            String xorrisoScript = "#!/bin/sh\n"
                    + "cd \"" + targetDirectory + "\"\n"
                    + "xorriso -dev \"" + isoPath + "\" -volid LERNSTICK";
            if (!(isoLabel.isEmpty())) {
                xorrisoScript += " -application_id \"" + isoLabel + "\"";
            }
            xorrisoScript += " -boot_image isolinux dir=isolinux"
                    + " -joliet on"
                    + " -compliance iso_9660_level=3"
                    + " -add .";

            int returnValue = PROCESS_EXECUTOR.executeScript(
                    true, true, xorrisoScript);

            PROCESS_EXECUTOR.removePropertyChangeListener(this);
            if (returnValue != 0) {
                return false;
            }

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return true;
    }

    @Override
    protected void done() {
        if (inhibit != null) {
            inhibit.delete();
        }
        try {
            dlCopyGUI.isoCreationFinished(isoPath, get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (ProcessExecutor.LINE.equals(propertyName)) {
            String line = (String) evt.getNewValue();
            switch (step) {
                case GENISOIMAGE:
                    Matcher matcher = XORRISO_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String progressString = matcher.group(1).trim();
                        try {
                            final int progress
                                    = Integer.parseInt(progressString);
                            String message = STRINGS.getString(
                                    "Creating_Image_Progress");
                            message = MessageFormat.format(
                                    message, progress + "%");
                            dlCopyGUI.showIsoProgressMessage(message, progress);
                        } catch (NumberFormatException ex) {
                            LOGGER.log(Level.WARNING,
                                    "could not parse xorriso progress", ex);
                        }
                    }

                    break;

                case MKSQUASHFS:
                    matcher = MKSQUASHFS_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String doneString = matcher.group(1).trim();
                        String maxString = matcher.group(2).trim();
                        try {
                            int doneInt = Integer.parseInt(doneString);
                            int maxInt = Integer.parseInt(maxString);
                            final int progress = (doneInt * 100) / maxInt;
                            String message = STRINGS.getString(
                                    "Compressing_Filesystem_Progress");
                            message = MessageFormat.format(
                                    message, progress + "%");
                            dlCopyGUI.showIsoProgressMessage(message, progress);
                        } catch (NumberFormatException ex) {
                            LOGGER.log(Level.WARNING,
                                    "could not parse mksquashfs progress", ex);
                        }
                    }
                    break;

                default:
                    LOGGER.log(Level.WARNING, "unsupported step {0}", step);
            }
        }
    }

    private void createSquashFS(String targetDirectory)
            throws IOException, DBusException {

        dlCopyGUI.showIsoProgressMessage(
                STRINGS.getString("Mounting_Partitions"));

        // mount all readonly squashfs files
        List<String> readOnlyMountPoints
                = LernstickFileTools.mountAllSquashFS(
                        systemSource.getSystemPath());

        // mount persistence (data partition)
        MountInfo dataMountInfo = systemSource.getDataPartition().mount();
        String dataPartitionPath = dataMountInfo.getMountPath();

        // create aufs union of squashfs files with persistence
        // ---------------------------------
        // We need an rwDir so that we can change some settings in the
        // lernstickWelcome properties below without affecting the current
        // data partition.
        // rwDir and cowDir are placed in /run/ because it is one of
        // the few directories that are not aufs itself.
        // Nested aufs is not (yet) supported...
        File runDir = new File("/run/");
        File rwDir = LernstickFileTools.createTempDirectory(runDir, "rw");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("br=");
        stringBuilder.append(rwDir.getPath());
        stringBuilder.append(':');
        stringBuilder.append(dataPartitionPath);
        // The additional option "=ro+wh" for the data partition is
        // absolutely neccessary! Otherwise the whiteouts (info about
        // deleted files) in the data partition are not applied!!!
        stringBuilder.append("=ro+wh");
        for (String readOnlyMountPoint : readOnlyMountPoints) {
            stringBuilder.append(':');
            stringBuilder.append(readOnlyMountPoint);
        }
        String branchDefinition = stringBuilder.toString();
        File cowDir = LernstickFileTools.mountAufs(branchDefinition);

        // apply settings in cow directory
        Properties lernstickWelcomeProperties = new Properties();
        File propertiesFile = new File(cowDir, "/etc/lernstickWelcome");
        try (FileReader reader = new FileReader(propertiesFile)) {
            lernstickWelcomeProperties.load(reader);
        } catch (IOException iOException) {
            LOGGER.log(Level.WARNING, "", iOException);
        }
        lernstickWelcomeProperties.setProperty("ShowNotUsedInfo",
                showNotUsedDialog ? "true" : "false");
        lernstickWelcomeProperties.setProperty("AutoStartInstaller",
                autoStartInstaller ? "true" : "false");
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            lernstickWelcomeProperties.store(
                    writer, "lernstick Welcome properties");
        } catch (IOException iOException) {
            LOGGER.log(Level.WARNING, "", iOException);
        }

        // remove ssh settings in cow directory to make it unique for every
        // system
        DLCopy.removeSshConfig(cowDir.getPath());

        // create new squashfs image
        step = Step.MKSQUASHFS;
        dlCopyGUI.showIsoProgressMessage(
                STRINGS.getString("Compressing_Filesystem"));
        PROCESS_EXECUTOR.addPropertyChangeListener(this);
        String cowPath = cowDir.getPath();
        int exitValue = PROCESS_EXECUTOR.executeProcess("mksquashfs",
                cowPath, targetDirectory + "/live/filesystem.squashfs",
                "-comp", "xz");
        if (exitValue != 0) {
            throw new IOException(
                    STRINGS.getString("Error_Creating_Squashfs"));
        }
        PROCESS_EXECUTOR.removePropertyChangeListener(this);

        // umount all partitions
        DLCopy.umount(cowPath, dlCopyGUI);
        if (!dataMountInfo.alreadyMounted()) {
            systemSource.getDataPartition().umount();
        }
        for (String readOnlyMountPoint : readOnlyMountPoints) {
            DLCopy.umount(readOnlyMountPoint, dlCopyGUI);
        }
        
        // remove all temporary directories
        cowDir.delete();
        LernstickFileTools.recursiveDelete(rwDir, true);
        LernstickFileTools.recursiveDelete(
                new File(readOnlyMountPoints.get(0)).getParentFile(), true);
    }
}
