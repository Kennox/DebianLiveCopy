package ch.fhnw.dlcopy;

import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.dlcopy.tools.DbusTools;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.udisks.Device;

/**
 * A storage device partition
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class Partition {

    private final static Logger LOGGER =
            Logger.getLogger(Partition.class.getName());
    private final String device;
    private final int number;
    private final long offset;
    private final long size;
    private final String type;
    private final String idLabel;
    private final String idType;
    private final String systemPartitionLabel;
    private Boolean isSystemPartition;
    private Long usedSpace;

    /**
     * creates a new Partition
     *
     * @param device the device of the partition (e.g. "sda1")
     * @param number the device number
     * @param offset the offset (start) of the partition
     * @param size the size of the partition
     * @param type the partition type
     * @param idLabel the label of the partition
     * @param idType the ID type (type of the file system)
     * @param systemPartitionLabel the (expected) system partition label
     */
    public Partition(String device, int number, long offset, long size,
            String type, String idLabel, String idType,
            String systemPartitionLabel) {
        this.device = device;
        this.number = number;
        this.offset = offset;
        this.size = size;
        this.idLabel = idLabel;
        this.idType = idType;
        this.type = type;
        this.systemPartitionLabel = systemPartitionLabel;
    }

    /**
     * returns the device of the partition, e.g. "sda1"
     *
     * @return the device of the partition, e.g. "sda1"
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns the partition number
     *
     * @return the partition number
     */
    public int getNumber() {
        return number;
    }

    /**
     * returns the start sector of the partition
     *
     * @return the start sector of the partition
     */
    public long getOffset() {
        return offset;
    }

    /**
     * returns the size of this partition
     *
     * @return the size of this partition
     */
    public long getSize() {
        return size;
    }

    /**
     * returns the label of the partition
     *
     * @return the label of the partition
     */
    public String getIdLabel() {
        return idLabel;
    }

    /**
     * returns the ID type of the partition
     *
     * @return the ID type of the partition
     */
    public String getIdType() {
        return idType;
    }

    /**
     * returns the partition type
     *
     * @return the partition type
     */
    public String getType() {
        return type;
    }

    /**
     * returns a list of mount paths of this partition
     *
     * @return a list of mount paths of this partition
     * @throws DBusException if a dbus exception occurs
     */
    public List<String> getMountPaths() throws DBusException {
        return DbusTools.getStringListProperty(device, "DeviceMountPaths");
    }

    /**
     * checks if the partition is an extended partition
     *
     * @return
     * <code>true</code>, if the partition is an extended partition,
     * <code>false</code> otherwise
     */
    public boolean isExtended() {
        return type.equals("0x05") || type.equals("0x0f");
    }

    /**
     * checks if the file system on the partition is ext[2|3|4]
     *
     * @return
     * <code>true</code>, if the file system on the partition is ext[2|3|4],
     * <code>false</code> otherwise
     */
    public boolean hasExtendedFilesystem() {
        return idType.equals("ext2")
                || idType.equals("ext3")
                || idType.equals("ext4");
    }

    /**
     * returns the used space on this partition
     *
     * @param onlyHomeAndCups calculate space only for "/home/user/" and
     * "/etc/cups/"
     * @return the free/usable space on this partition or "-1" if the usable
     * space is unknown
     */
    public long getUsedSpace(boolean onlyHomeAndCups) {
        try {
            if (usedSpace == null) {

                // mount partition if not already mounted
                boolean tmpMount = false;
                List<String> mountPaths = getMountPaths();
                String mountPath;
                if (mountPaths.isEmpty()) {
                    mountPath = mount();
                    tmpMount = true;
                } else {
                    mountPath = mountPaths.get(0);
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "{0} already mounted at {1}",
                                new Object[]{device, mountPath});
                    }
                }

                if (onlyHomeAndCups) {
                    // in case of an upgrade we would only keep /home/user and
                    // /etc/cups
                    long userSize = 0;
                    long cupsSize = 0;
                    ProcessExecutor processExecutor = new ProcessExecutor();
                    Pattern pattern = Pattern.compile("^(\\d+).*");
                    processExecutor.executeProcess(true, true,
                            "du", "-sb", mountPath + "/home/user");
                    String stdOut = processExecutor.getStdOut();
                    LOGGER.log(Level.INFO, "stdOut = \"{0}\"", stdOut);
                    Matcher matcher = pattern.matcher(stdOut);
                    if (matcher.find()) {
                        String userSizeString = matcher.group(1);
                        userSize = Long.parseLong(userSizeString);
                    }
                    LOGGER.log(Level.INFO, "userSize = {0}", userSize);

                    processExecutor.executeProcess(true, true,
                            "du", "-sb", mountPath + "/etc/cups");
                    matcher = pattern.matcher(processExecutor.getStdOut());
                    if (matcher.find()) {
                        String userSizeString = matcher.group(1);
                        cupsSize = Long.parseLong(userSizeString);
                    }
                    LOGGER.log(Level.INFO, "cupsSize = {0}", cupsSize);
                    usedSpace = userSize + cupsSize;

                } else {
                    usedSpace = size - (new File(mountPath)).getUsableSpace();
                }

                LOGGER.log(Level.INFO, "usedSpace = {0}", usedSpace);

                // cleanup
                if (tmpMount) {
                    umount();
                }
            }
        } catch (DBusExecutionException ex) {
            LOGGER.log(Level.WARNING, "", ex);
            usedSpace = -1l;
        } catch (DBusException ex) {
            LOGGER.log(Level.WARNING, "", ex);
            usedSpace = -1l;
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING, "", ex);
            usedSpace = -1l;
        }

        return usedSpace;
    }

    /**
     * mounts this partition via dbus/udisks
     *
     * @return the mount point
     * @throws DBusException if a dbus exception occurs
     */
    public String mount() throws DBusException {
        List<String> mountPaths = getMountPaths();
        if (mountPaths.isEmpty()) {
            return DbusTools.getDevice(device).FilesystemMount(
                    "auto", new ArrayList<String>());
        } else {
            String mountPath = mountPaths.get(0);
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "{0} already mounted at {1}",
                        new Object[]{device, mountPath});
            }
            return mountPath;
        }
    }

    /**
     * umounts this partition via dbus/udisks
     *
     * @return
     * <code>true</code>, if the umount operation succeeded,
     * <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean umount() throws DBusException {
        /**
         * TODO: umount timeout problem: when there have been previous copy
         * operations, this call very often fails with the following exception:
         * org.freedesktop.DBus$Error$NoReply: No reply within specified time at
         * org.freedesktop.dbus.RemoteInvocationHandler.executeRemoteMethod(RemoteInvocationHandler.java:133)
         * at
         * org.freedesktop.dbus.RemoteInvocationHandler.invoke(RemoteInvocationHandler.java:188)
         * at $Proxy2.FilesystemUnmount(Unknown Source)
         */
        boolean success = false;
        for (int i = 0; !success && (i < 10); i++) {
            // it already happend that during the timeout
            // in handleUmountException() the umount call succeeded!
            // therefore we need to test for the mount status in every round
            // and act accordingly...
            if (isMounted()) {
                LOGGER.log(Level.INFO,
                        "/dev/{0} is mounted, calling umount...", device);
                try {
                    Device dbusDevice = DbusTools.getDevice(device);
                    dbusDevice.FilesystemUnmount(new ArrayList<String>());
                    success = true;
                } catch (DBusException ex) {
                    handleUmountException(ex);
                } catch (DBusExecutionException ex) {
                    handleUmountException(ex);
                }
            } else {
                LOGGER.log(Level.INFO,
                        "/dev/{0} was NOT mounted", device);
                success = true;
            }
        }
        if (!success) {
            LOGGER.log(Level.SEVERE, "Could not umount /dev/{0}", device);
        }
        return success;
    }

    /**
     * returns
     * <code>true</code>, if this partition is a Debian Live system partition,
     * <code>false</code> otherwise
     *
     * @return
     * <code>true</code>, if this partition is a Debian Live system partition,
     * <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean isSystemPartition() throws DBusException {
        if (isSystemPartition == null) {
            isSystemPartition = false;
            LOGGER.log(Level.FINEST, "checking partition {0}", device);
            LOGGER.log(Level.FINEST, "partition label: \"{0}\"", idLabel);
            if (systemPartitionLabel.equals(idLabel)) {
                // mount partition if not already mounted
                boolean tmpMount = false;
                List<String> mountPaths = getMountPaths();
                String mountPath;
                if (mountPaths.isEmpty()) {
                    mountPath = mount();
                    tmpMount = true;
                } else {
                    mountPath = mountPaths.get(0);
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "{0} already mounted at {1}",
                                new Object[]{device, mountPath});
                    }
                }

                // check partition file structure
                LOGGER.log(Level.FINEST,
                        "checking file structure on partition {0}", device);
                File liveDir = new File(mountPath, "live");
                if (liveDir.exists()) {
                    FilenameFilter squashFsFilter = new FilenameFilter() {

                        @Override
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".squashfs");
                        }
                    };
                    String[] squashFileSystems = liveDir.list(squashFsFilter);
                    isSystemPartition = (squashFileSystems.length > 0);
                }

                // cleanup
                if (tmpMount) {
                    umount();
                }
            } else {
                LOGGER.finest("does not match system partition label");
            }
        }
        return isSystemPartition;
    }

    /**
     * returns
     * <code>true</code>, if this partition is a Debian Live persistency
     * partition,
     * <code>false</code> otherwise
     *
     * @return
     * <code>true</code>, if this partition is a Debian Live persistency
     * partition,
     * <code>false</code> otherwise
     */
    public boolean isPersistencyPartition() {
        return idLabel.equals("live-rw");
    }

    /**
     * returns
     * <code>true</code>, if this partition is an active persistency partition,
     * <code>false</code> otherwise
     *
     * @return
     * <code>true</code>, if this partition is an active persistency partition,
     * <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean isActivePersistencyPartition() throws DBusException {
        if (isPersistencyPartition()) {
            List<String> mountPaths = getMountPaths();
            for (String mountPath : mountPaths) {
                if (mountPath.equals("/live/cow")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * returns
     * <code>true</code>, if the partition is mounted,
     * <code>false</code> otherwise
     *
     * @return
     * <code>true</code>, if the partition is mounted,
     * <code>false</code> otherwise
     * @throws DBusException if an dbus exception occurs
     */
    public boolean isMounted() throws DBusException {
        List<String> mountPaths = getMountPaths();
        return (mountPaths != null) && (!mountPaths.isEmpty());
    }

    private void handleUmountException(Exception ex) {
        LOGGER.log(Level.WARNING, "", ex);
        try {
            ProcessExecutor processExecutor = new ProcessExecutor();
            int returnValue = processExecutor.executeProcess(true, true,
                    "fuser", "-m", "/dev/" + device);
            while (returnValue == 0) {
                LOGGER.log(Level.INFO, "/dev/{0} is still being used by the "
                        + "following processes:\n{1}",
                        new Object[]{device, processExecutor.getStdOut()});
                Thread.sleep(1000);
                returnValue = processExecutor.executeProcess(true, true,
                        "fuser", "-m", "/dev/" + device);
            }
        } catch (InterruptedException ex2) {
            LOGGER.log(Level.SEVERE, "", ex2);
        }
    }
}