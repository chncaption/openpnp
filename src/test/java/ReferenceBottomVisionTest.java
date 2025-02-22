import java.io.File;

import org.junit.jupiter.api.Test;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.camera.SimulatedUpCamera;
import org.openpnp.machine.reference.driver.NullDriver;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PartAlignment.PartAlignmentOffset;
import org.openpnp.util.VisionUtils;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;

import com.google.common.io.Files;

public class ReferenceBottomVisionTest {
    @Test
    public void testPositiveAngle() throws Exception {
        testError(new Location(LengthUnit.Millimeters, 1, 2, 0, 13));
    }
    
    @Test
    public void testNegativeAngle() throws Exception {
        testError(new Location(LengthUnit.Millimeters, 1, 2, 0, -13));
    }
    
    public static void testError(Location error) throws Exception {
        Location maxError = new Location(LengthUnit.Millimeters, 0.1, 0.1, 0, 0.03);

        File workingDirectory = Files.createTempDir();
        workingDirectory = new File(workingDirectory, ".openpnp");
        System.out.println("Configuration directory: " + workingDirectory);

        Configuration.initialize(workingDirectory);
        Configuration.get().load();
        // Save migrated.
        Configuration.get().save();

        Configurator
        .currentConfig()
        .level(Level.INFO) // change this for other log levels.
        .activate();

        Machine machine = Configuration.get().getMachine();
        Nozzle nozzle = machine.getDefaultHead().getDefaultNozzle();
        SimulatedUpCamera camera = (SimulatedUpCamera) VisionUtils.getBottomVisionCamera();
        Part part = Configuration.get().getPart("R0805-1K"); 
        ReferenceBottomVision bottomVision = ReferenceBottomVision.getDefault();
        NullDriver driver = (NullDriver) ((ReferenceMachine) machine).getDefaultDriver();
        driver.setFeedRateMmPerMinute(0);

        // Set nozzle tip pick tolerances for large offsets.
        for (NozzleTip tip : Configuration.get().getMachine().getNozzleTips()) {
            ((ReferenceNozzleTip) tip).setMaxPickTolerance(new Length(3, LengthUnit.Millimeters));
        }

        camera.setErrorOffsets(error);
        machine.setEnabled(true);
        machine.execute(() -> {
            nozzle.pick(part);
            PartAlignmentOffset offset = bottomVision.findOffsets(part, null, null, nozzle);
            Location offsets = offset.getLocation();
            assertMaxDelta(offsets.getX(), error.getX(), maxError.getX());
            assertMaxDelta(offsets.getY(), error.getY(), maxError.getY());
            assertMaxDelta(offsets.getRotation(), error.getRotation(), maxError.getRotation());
            return true;
        });
    }
    
    public static void assertMaxDelta(double a, double b, double maxDelta) throws Exception {
        if (Math.abs(a - b) > maxDelta) {
            throw new Exception(String.format("abs(%f - %f) > %f", a, b, maxDelta));
        }
    }
}
