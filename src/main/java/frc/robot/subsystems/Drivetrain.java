package frc.robot.subsystems;

import static frc.robot.core.TalonSwerve.SwerveConstants.*;

import com.pathplanner.lib.commands.FollowPathHolonomic;
import com.pathplanner.lib.path.PathPlannerPath;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.PIDConstants;
import com.pathplanner.lib.util.ReplanningConfig;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.*;
import frc.robot.RobotMap.DriveMap;
import frc.robot.core.MAXSwerve.MAXSwerve;
import frc.robot.core.MAXSwerve.MAXSwerveModule;
import frc.robot.core.TalonSwerve.Swerve;
import frc.robot.core.TalonSwerve.SwerveConstants;
import java.util.function.Supplier;

/**
 * <b>Use {@link #getInstance()} to access all subsystems.</b><br>
 * <br>
 * This is the Drive subsystem for the 2024 season. Throughout the season, add everything driving
 * here. <br>
 * <br>
 * Think:
 *
 * <ul>
 *   <li>path following (including OTF and replanning),
 *   <li>slew-rate limiting,
 *   <li>AprilTag relocalization and alignment,
 *   <li>object and color alignment
 *   <li>whatever else you'd like!
 * </ul>
 *
 * Check out the core folder for the base drivetrain methods already implemented ({@link MAXSwerve}
 * or {@link Swerve} for this season, which this class extends). As of now, this includes basic
 * driving and odometry, so you shouldn't try to reinvent the wheel.
 */
public class Drivetrain extends MAXSwerve {
  private static Drivetrain instance;

  /**
   * <b>Use this to access the subsystem using its static instance.</b><br>
   * Tweak constructor values (port configs) in {@link frc.robot.RobotMap.DriveMap}
   *
   * @return the singleton of this class. Use this to reference the drivetrain between classes and
   *     Commands.
   */
  public static Drivetrain getInstance() {
    if (instance == null) instance = new Drivetrain();
    return instance;
  }

  /**
   * This constructor is intentionally left private. <br>
   * <b>Do <i>not</i> attempt to create new instances of any subsystem using the constructor.</b>
   *
   * @see #getInstance
   */
  private Drivetrain() {
    super(
        DriveMap.PIGEON_ID,
        new MAXSwerveModule(
            DriveMap.FrontLeft.DRIVE_ID,
            DriveMap.FrontLeft.ROTATOR_ID,
            DriveMap.FrontLeft.ANGULAR_OFFSET),
        new MAXSwerveModule(
            DriveMap.FrontRight.DRIVE_ID,
            DriveMap.FrontRight.ROTATOR_ID,
            DriveMap.FrontRight.ANGULAR_OFFSET),
        new MAXSwerveModule(
            DriveMap.BackLeft.DRIVE_ID,
            DriveMap.BackLeft.ROTATOR_ID,
            DriveMap.BackLeft.ANGULAR_OFFSET),
        new MAXSwerveModule(
            DriveMap.BackRight.DRIVE_ID,
            DriveMap.BackRight.ROTATOR_ID,
            DriveMap.BackRight.ANGULAR_OFFSET));
  }

  /**
   * TODO tune PID gains for x, y, and rot under {@link SwerveConstants} <br>
   * <br>
   * A method to travel to a given position on the field.
   *
   * @param targetPose a {@link Pose2d} representing the pose to travel to.
   * @return a Command that continuously attempts to converge to targetPose until the controllers'
   *     thresholds have been reached.
   */
  public Command goToPoint(Pose2d targetPose) {
    PIDController xController = new PIDController(X_KP, X_KI, X_KD);
    PIDController yController = new PIDController(Y_KP, Y_KI, Y_KD);
    PIDController thetaController = new PIDController(THETA_KP, THETA_KI, THETA_KD);

    return new FunctionalCommand(
        () ->
            System.out.println(
                String.format(
                    "Traveling to x:%s, y:%s, z:%s",
                    targetPose.getX(), targetPose.getY(), targetPose.getRotation().getDegrees())),
        () -> {
          double sX = xController.calculate(getPose().getX(), targetPose.getX());
          double sY = yController.calculate(getPose().getY(), targetPose.getY());
          double sR =
              thetaController.calculate(
                  getPose().getRotation().getRadians(), targetPose.getRotation().getRadians());

          // need to get rotpos? felt cute now might delete later (ꈍᴗꈍ)♡
          // drive(ChassisSpeeds.fromFieldRelativeSpeeds(sX, sY, sR, getPose().getRotation()),
          // true);
          drive(sX, sY, sR, true, true);
        },
        interrupted -> {
          xController.close();
          yController.close();
          thetaController.close();
        },
        () -> xController.atSetpoint() && yController.atSetpoint() && thetaController.atSetpoint(),
        /* or getPose().getTranslation().getDistance(targetPose.getTranslation()) < 0.1 ?*/
        this);
  }

  /**
   * @see #followPathCommand(Supplier)
   * @param pathName a String representing the name of the path. These should be automatically added
   *     to src/main/deploy/pathplanner/paths.
   * @return a Command that follows the path.
   */
  public Command followPathCommand(String pathName) {
    PathPlannerPath path = PathPlannerPath.fromPathFile(pathName);

    return followPathCommand(() -> path);
  }

  /**
   * The base method to follow a PathPlanner path.
   *
   * @see #followPathCommand(String)
   * @param pathSupplier a Supplier that gives
   * @return a Command that follows the path.
   */
  public Command followPathCommand(Supplier<PathPlannerPath> pathSupplier) {
    return new FollowPathHolonomic(
        pathSupplier.get(),
        this
            ::getPose, // Robot pose supplier - TODO replace this with vision corrected pose from
                       // Pose.java (Pose.getInstance().getPosition())
        () ->
            ChassisSpeeds.fromFieldRelativeSpeeds(
                KINEMATICS.toChassisSpeeds(),
                new Rotation2d(getHeading())), // ChassisSpeeds supplier. MUST BE ROBOT RELATIVE
        this::drivePP, // Method that will drive the robot given ROBOT RELATIVE ChassisSpeeds
        new HolonomicPathFollowerConfig( // HolonomicPathFollowerConfig, this should likely live in
            // your Constants class
            new PIDConstants(5.0, 0.0, 0.0), // Translation PID constants
            new PIDConstants(5.0, 0.0, 0.0), // Rotation PID constants
            4.5, // Max module speed, in m/s
            0.4, // Drive base radius in meters. Distance from robot center to furthest module.
            new ReplanningConfig() // Default path replanning config. See the API for the options
            // here
            ),
        () -> {
          // Boolean supplier that controls when the path will be mirrored for the red alliance
          // This will flip the path being followed to the red side of the field.
          // THE ORIGIN WILL REMAIN ON THE BLUE SIDE

          var alliance = DriverStation.getAlliance();
          if (alliance.isPresent()) {
            return alliance.get() == DriverStation.Alliance.Red;
          }
          return false;
        },
        this // Reference to this subsystem to set requirements
        );
  }

  /**
   * This method is called once per cycle – or "period", hence its name. On the RoboRIO, this means
   * code inside is run once every ~10ms, or one hundredths of a second. Put everything that should
   * be running throughout the entirety of the match in this method. <br>
   * <br>
   * <i>Usually,</i> the drivetrain needs to do nothing more than repeatedly update odometry.
   */
  @Override
  public void periodic() {
    super.periodic();
  }
}
