/*
 * UsbRenderer.java
 *
 * Created on 16. April 2008, 13:23
 */
package dlcopy;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.ListCellRenderer;
import dlcopy.DLCopy.PartitionState;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.ImageIcon;

/**
 * A renderer for storage devices
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class StorageDeviceRenderer extends JPanel implements ListCellRenderer {

    public enum State {

        Selection, PartitionCreation,
        OperatingSystemCopy, DataPartitionCopy, ExchangePartitionCopy
    }
    private final static Logger LOGGER =
            Logger.getLogger(DLCopy.class.getName());
    private final static int OFFSET = 5;
    private final static int BAR_HEIGHT = 30;
    private final DLCopy dlCopy;
    private final long systemSize;
    private final Color LIGHT_BLUE = new Color(170, 170, 255);
    private long maxUsbStorageSize;
    private StorageDevice storageDevice;
    private boolean drawSeparator;
    private boolean isSelected;
    private State state;
    private final int iconInsets;
    private int iconGap;

    /** Creates new form UsbRenderer
     * @param systemSize the size of the system to be copied in Byte
     */
    public StorageDeviceRenderer(DLCopy dlCopy, long systemSize) {
        this.dlCopy = dlCopy;
        this.systemSize = systemSize;
        initComponents();
        state = State.Selection;
        GridBagLayout layout = (GridBagLayout) getLayout();
        Insets insets = layout.getConstraints(iconLabel).insets;
        iconInsets = insets.left + insets.right;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        if (value instanceof StorageDevice) {
            this.storageDevice = (StorageDevice) value;
            if (isSelected) {
                setBackground(list.getSelectionBackground());
            } else {
                setBackground(list.getBackground());
            }
        }
        this.isSelected = isSelected;
        drawSeparator = (index != (list.getModel().getSize() - 1));

        return this;
    }

    /**
     * sets the current state
     * @param state the state to set
     */
    public void setState(State state) {
        this.state = state;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // set device text and icon based on storage type
        String deviceText = null;
        long usbStorageSize = storageDevice.getSize();
        if (storageDevice instanceof UsbStorageDevice) {
            UsbStorageDevice usbStorageDevice =
                    (UsbStorageDevice) storageDevice;
            deviceText = usbStorageDevice.getVendor() + " "
                    + usbStorageDevice.getModel() + ", "
                    + DLCopy.getDataVolumeString(usbStorageSize, 1) + " (/dev/"
                    + usbStorageDevice.getDevice() + ")";
            iconLabel.setIcon(new ImageIcon(getClass().getResource(
                    "/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png")));
        } else if (storageDevice instanceof Harddisk) {
            Harddisk harddisk = (Harddisk) storageDevice;
            deviceText = harddisk.getVendor() + " "
                    + harddisk.getModel() + ", "
                    + DLCopy.getDataVolumeString(usbStorageSize, 1) + " (/dev/"
                    + harddisk.getDevice() + ")";
            iconLabel.setIcon(new ImageIcon(getClass().getResource(
                    "/dlcopy/icons/32x32/drive-harddisk.png")));
        } else if (storageDevice instanceof SDStorageDevice) {
            SDStorageDevice sdStorageDevice =
                    (SDStorageDevice) storageDevice;
            deviceText = sdStorageDevice.getName() + " "
                    + DLCopy.getDataVolumeString(usbStorageSize, 1) + " (/dev/"
                    + sdStorageDevice.getDevice() + ")";
            iconLabel.setIcon(new ImageIcon(getClass().getResource(
                    "/dlcopy/icons/32x32/media-flash-sd-mmc.png")));
        } else {
            LOGGER.log(Level.WARNING,
                    "unsupported storage device: {0}", storageDevice);
        }
        iconGap = iconLabel.getWidth() + iconInsets;
        Graphics2D graphics2D = (Graphics2D) g;
        int componentWidth = getWidth();
        int height = getHeight();
        long overhead = usbStorageSize - systemSize;
        int usbStorageWidth = (int) (((componentWidth - iconGap - 2 * OFFSET)
                * usbStorageSize) / maxUsbStorageSize);
        PartitionState partitionState =
                DLCopy.getPartitionState(usbStorageSize, systemSize);

        // set painter for text
        graphics2D.setPaint(Color.BLACK);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // draw top text
        int rectangleTop = 0;
        if (partitionState == PartitionState.TOO_SMALL) {
            rectangleTop = drawTopText(graphics2D, deviceText);
        } else {
            String text = DLCopy.STRINGS.getString("Proposed_Partitioning");
            text = MessageFormat.format(text, deviceText);
            rectangleTop = drawTopText(graphics2D, text);
        }

        // paint usb stick rectangle
        if (partitionState == PartitionState.TOO_SMALL) {
            graphics2D.setPaint(Color.GRAY);
        } else {
            graphics2D.setPaint(LIGHT_BLUE);
        }
        graphics2D.fill3DRect(iconGap + OFFSET, rectangleTop,
                usbStorageWidth, BAR_HEIGHT, true);

        // do not paint exchange partition when not selected
        if ((partitionState == PartitionState.EXCHANGE) && !isSelected) {
            partitionState = PartitionState.PERSISTENT;
        }

        // paint additional blocks and texts
        switch (partitionState) {
            case TOO_SMALL:
                // paint error text
                String errorText = DLCopy.STRINGS.getString("Too_Small");
                drawCenterText(iconGap + OFFSET, rectangleTop, usbStorageWidth,
                        BAR_HEIGHT, errorText, errorText, graphics2D);
                break;

            case ONLY_SYSTEM:
                // paint OS text
                String usbStorageSizeText =
                        DLCopy.getDataVolumeString(usbStorageSize, 1) + " ";
                String text = usbStorageSizeText
                        + DLCopy.STRINGS.getString("Operating_System");
                String shortText = usbStorageSizeText
                        + DLCopy.STRINGS.getString("Operating_System_Short");
                drawCenterText(iconGap + OFFSET, rectangleTop, usbStorageWidth,
                        BAR_HEIGHT, text, shortText, graphics2D);
                break;

            case PERSISTENT:
                // block widths
                int persistentWidth =
                        (int) ((usbStorageWidth * overhead) / usbStorageSize);
                int systemWidth = usbStorageWidth - persistentWidth;
                // texts
                String persistentText = DLCopy.getDataVolumeString(
                        overhead, 1) + " " + DLCopy.STRINGS.getString("Data");
                String systemSizeString =
                        DLCopy.getDataVolumeString(systemSize, 1) + " ";
                String systemText = systemSizeString
                        + DLCopy.STRINGS.getString("Operating_System");
                String systemTextShort = systemSizeString
                        + DLCopy.STRINGS.getString("Operating_System_Short");
                // paint persistent partition in green
                graphics2D.setPaint(Color.GREEN);
                graphics2D.fill3DRect(iconGap + OFFSET, rectangleTop,
                        persistentWidth, BAR_HEIGHT, true);
                // paint block texts
                drawCenterText(iconGap + OFFSET, rectangleTop, persistentWidth,
                        BAR_HEIGHT, persistentText, persistentText, graphics2D);
                int systemPartitionX = iconGap + OFFSET + persistentWidth;
                drawCenterText(systemPartitionX, rectangleTop, systemWidth,
                        BAR_HEIGHT, systemText, systemTextShort, graphics2D);
                break;

            case EXCHANGE:
                // block widths
                systemWidth = (int) ((usbStorageWidth * systemSize)
                        / usbStorageSize);
                JSlider getExchangePartitionSizeSlider =
                        dlCopy.getExchangePartitionSizeSlider();
                long exchangeSize =
                        (long) getExchangePartitionSizeSlider.getValue()
                        * DLCopy.MEGA;
                int exchangeWidth = (int) ((usbStorageWidth * exchangeSize)
                        / usbStorageSize);
                int maximumExchangeSizeMega =
                        getExchangePartitionSizeSlider.getMaximum();
                long maximumExchangeSize =
                        (long) maximumExchangeSizeMega * DLCopy.MEGA;
                long persistentSize = 0;
                persistentWidth = 0;
                // we need to calculate with overheadMega because we define MiB
                // in the exchange partition size slider
                // (calculating with byte values is "too exact"...)
                long overheadMega = overhead / DLCopy.MEGA;
                if ((overheadMega != maximumExchangeSizeMega)
                        || (exchangeSize != maximumExchangeSize)) {
                    persistentSize = usbStorageSize - systemSize - exchangeSize;
                    persistentWidth =
                            usbStorageWidth - exchangeWidth - systemWidth;
                }

                // texts
                String exchangeTextShort =
                        DLCopy.getDataVolumeString(exchangeSize, 1);
                String exchangeText = exchangeTextShort + " "
                        + DLCopy.STRINGS.getString("Exchange");
                String persistentTextShort =
                        DLCopy.getDataVolumeString(persistentSize, 1);
                persistentText = persistentTextShort + " "
                        + DLCopy.STRINGS.getString("Data");
                systemSizeString =
                        DLCopy.getDataVolumeString(systemSize, 1) + " ";
                systemText = systemSizeString
                        + DLCopy.STRINGS.getString("Operating_System");
                systemTextShort = systemSizeString
                        + DLCopy.STRINGS.getString("Operating_System_Short");

                int persistentPartitionX = iconGap + OFFSET + exchangeWidth;

                // paint color blocks first and texts later
                // this way the persistent color block can not overwrite the
                // exchange text...
                if (exchangeWidth > 0) {
                    graphics2D.setPaint(Color.YELLOW);
                    graphics2D.fill3DRect(iconGap + OFFSET, rectangleTop,
                            exchangeWidth, BAR_HEIGHT, true);
                }
                if (persistentWidth > 0) {
                    graphics2D.setPaint(Color.GREEN);
                    graphics2D.fill3DRect(persistentPartitionX, rectangleTop,
                            persistentWidth, BAR_HEIGHT, true);
                }
                if (exchangeWidth > 0) {
                    drawCenterText(iconGap + OFFSET, rectangleTop, exchangeWidth,
                            BAR_HEIGHT, exchangeText, exchangeTextShort,
                            graphics2D);
                }
                if (persistentWidth > 0) {
                    drawCenterText(persistentPartitionX, rectangleTop,
                            persistentWidth, BAR_HEIGHT,
                            persistentText, persistentTextShort, graphics2D);
                }
                systemPartitionX = persistentPartitionX + persistentWidth;
                drawCenterText(systemPartitionX, rectangleTop, systemWidth,
                        BAR_HEIGHT, systemText, systemTextShort, graphics2D);
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "unknown partitionState \"{0}\"", partitionState);
        }

        if (drawSeparator) {
            graphics2D.setPaint(Color.BLACK);
            int separatorPosition = height - 1;
            graphics2D.drawLine(
                    0, separatorPosition, componentWidth, separatorPosition);
        }
    }

    /**
     * sets the size of the largest USB stick
     * @param maxSize the size of the largest USB stick
     */
    public void setMaxSize(long maxSize) {
        this.maxUsbStorageSize = maxSize;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        iconLabel = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(340, 70));
        setLayout(new java.awt.GridBagLayout());

        iconLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        add(iconLabel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel iconLabel;
    // End of variables declaration//GEN-END:variables

    private int drawTopText(Graphics2D graphics2D, String text) {
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        Rectangle2D stringBounds =
                fontMetrics.getStringBounds(text, graphics2D);
        int stringHeight = (int) stringBounds.getHeight();
        graphics2D.drawString(text,
                iconGap + OFFSET,
                OFFSET - (int) stringBounds.getY());
        return stringHeight + 2 * OFFSET;
    }

    private void drawCenterText(int x, int y, int width, int height,
            String text, String shortText, Graphics2D graphics2D) {
        Font originalFont = graphics2D.getFont();

        // check if we can use the (long) text
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        Rectangle2D stringBounds = fontMetrics.getStringBounds(
                text, graphics2D);
        int stringWidth = (int) stringBounds.getWidth() + 2;
        if (stringWidth > width) {
            // no, we must use the short text
            text = shortText;
        }

        stringBounds = setFont(graphics2D, text, width);
        stringWidth = (int) stringBounds.getWidth();
        int stringHeight = (int) stringBounds.getHeight();
        graphics2D.setPaint(Color.BLACK);
        int textX = x + (width - stringWidth) / 2;
        int textY = y + (height - stringHeight) / 2
                - (int) stringBounds.getY();
        graphics2D.drawString(text, textX, textY);
        graphics2D.setFont(originalFont);
    }

    private Rectangle2D setFont(
            Graphics2D graphics2D, String string, int width) {
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        Font font = graphics2D.getFont();
        for (int stringWidth = width + 1; stringWidth > width;) {
            Rectangle2D stringBounds = fontMetrics.getStringBounds(
                    string, graphics2D);
            stringWidth = (int) stringBounds.getWidth() + 2;
            //System.out.println("stringWidth: " + stringWidth);
            if ((font.getSize() > 7) && (stringWidth > width)) {
                //System.out.println("old font: " + font);
                font = font.deriveFont(font.getSize() - 1f);
                //System.out.println("new font: " + font);
                graphics2D.setFont(font);
                fontMetrics = graphics2D.getFontMetrics();
            } else {
                return stringBounds;
            }
        }
        return null;
    }
}
