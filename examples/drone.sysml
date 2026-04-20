package AutonomousDrone {

  import SI::*;
  import ScalarValues::*;

  // =========================
  // REQUIREMENTS (Normalized)
  // =========================

  requirement REQ_FUNC_NAVIGATION {
    doc "Navigate autonomously to GPS waypoints with position error < 1 m.";
  }

  requirement REQ_FUNC_OBSTACLE_AVOIDANCE {
    doc "Detect and avoid obstacles within 15 m.";
  }

  requirement REQ_FUNC_PAYLOAD {
    doc "Carry payload up to 2.5 kg.";
  }

  requirement REQ_PERF_FLIGHT {
    doc "Maintain roll/pitch deviation < 0.5 deg and max airspeed >= 15 m/s.";
  }

  requirement REQ_PERF_ENDURANCE {
    doc "Flight endurance >= 25 min at full payload.";
  }

  requirement REQ_SAFE_BATTERY {
    doc "Auto-land if battery < 15%.";
  }

  requirement REQ_SAFE_COMMS {
    doc "Emergency landing if communication loss > 10 s.";
  }

  requirement REQ_SAFE_SELFTEST {
    doc "System shall not operate if sensor self-test fails.";
  }

  requirement REQ_INTF_COMM {
    doc "Secure real-time communication with ground control.";
  }

  requirement REQ_CONSTRAINT_AIRSPACE {
    doc "Operate within regulated urban airspace.";
  }

  // =========================
  // INTERFACES
  // =========================

  interface FlightCommandIF {
    out commands : FlightCommands;
  }

  interface StatusIF {
    out status : StatusUpdate;
  }

  interface BatteryIF {
    out battery : BatteryStatus;
  }

  interface SafetyIF {
    out safety : SafetyCommands;
  }

  // =========================
  // SYSTEM
  // =========================

  systemdef AutonomousDroneSystem {

    part nav : NavigationSubsystem;
    part obs : ObstacleDetectionSubsystem;
    part payload : PayloadSubsystem;
    part power : PowerSubsystem;
    part comm : CommunicationSubsystem;
    part fc : FlightControlSubsystem;
    part safety : SafetySubsystem;
    part compliance : ComplianceSubsystem;
    part ground : GroundControl;

    // Connections (closed and typed)

    connect nav.outgoingFlightPath to fc.incomingFlightCommands;
    connect obs.obstacleAlert to nav.obstacleInput;
    connect payload.payloadStatus to fc.payloadInfo;
    connect power.batteryStatus to safety.batteryMonitor;
    connect power.batteryStatus to fc.batteryInfo;
    connect comm.missionCommands to nav.missionInput;
    connect fc.flightStatus to comm.statusUpdates;
    connect comm.statusUpdates to ground.statusReceiver;
    connect safety.safetyCommands to fc.safetyInput;
    connect compliance.regulationData to safety.regulationInput;

    satisfy REQ_FUNC_NAVIGATION,
            REQ_FUNC_OBSTACLE_AVOIDANCE,
            REQ_FUNC_PAYLOAD,
            REQ_PERF_FLIGHT,
            REQ_PERF_ENDURANCE,
            REQ_SAFE_BATTERY,
            REQ_SAFE_COMMS,
            REQ_SAFE_SELFTEST,
            REQ_INTF_COMM,
            REQ_CONSTRAINT_AIRSPACE;
  }

  // =========================
  // SUBSYSTEMS
  // =========================

  partdef NavigationSubsystem {

    attribute gpsPrecision : Length = 0.8[m];

    port missionInput : in MissionPlan;
    port obstacleInput : in ObstacleAlert;
    port outgoingFlightPath : out FlightCommands;

    state behavior {
      state Navigate {
        accept missionInput;
        do computePath();
        accept obstacleInput;
        do replan();
        send outgoingFlightPath;
      }
    }

    action computePath();
    action replan();

    satisfy REQ_FUNC_NAVIGATION;
  }

  partdef ObstacleDetectionSubsystem {

    attribute detectionRange : Length = 15[m];

    port obstacleAlert : out ObstacleAlert;

    state behavior {
      state Monitor {
        do scan();
        if detected() then send obstacleAlert;
      }
    }

    action scan();
    action detected() : Boolean;

    satisfy REQ_FUNC_OBSTACLE_AVOIDANCE;
  }

  partdef PayloadSubsystem {

    attribute maxPayload : Mass = 2.5[kg];

    port payloadStatus : out PayloadStatus;

    state behavior {
      state Manage {
        do stabilize();
        send payloadStatus;
      }
    }

    action stabilize();

    satisfy REQ_FUNC_PAYLOAD;
  }

  partdef PowerSubsystem {

    attribute batteryLevel : Real = 100[%];

    port batteryStatus : out BatteryStatus;

    state behavior {
      state Monitor {
        send batteryStatus;
      }
    }

    satisfy REQ_SAFE_BATTERY;
  }

  partdef CommunicationSubsystem {

    port missionCommands : in MissionPlan;
    port statusUpdates : out StatusUpdate;

    state behavior {
      state Link {
        accept missionCommands;
        send statusUpdates;
      }
    }

    satisfy REQ_INTF_COMM;
  }

  partdef FlightControlSubsystem {

    attribute maxDeviation : Angle = 0.5[deg];
    attribute maxSpeed : Velocity = 15[m/s];

    port incomingFlightCommands : in FlightCommands;
    port batteryInfo : in BatteryStatus;
    port payloadInfo : in PayloadStatus;
    port safetyInput : in SafetyCommands;
    port flightStatus : out FlightStatus;

    state behavior {
      state Control {
        accept incomingFlightCommands;
        do control();
        send flightStatus;
      }
    }

    action control();

    satisfy REQ_PERF_FLIGHT;
  }

  partdef SafetySubsystem {

    port batteryMonitor : in BatteryStatus;
    port regulationInput : in RegulationData;
    port safetyCommands : out SafetyCommands;

    state behavior {
      state Monitor {
        accept batteryMonitor;
        if batteryMonitor.batteryChargePercent < 15[%]
          then sendAutoLand();
      }
    }

    action sendAutoLand();

    satisfy REQ_SAFE_BATTERY, REQ_SAFE_COMMS, REQ_SAFE_SELFTEST;
  }

  partdef ComplianceSubsystem {

    port regulationData : out RegulationData;

    state behavior {
      state Check {
        send regulationData;
      }
    }

    satisfy REQ_CONSTRAINT_AIRSPACE;
  }

  // =========================
  // DATA TYPES
  // =========================

  datatype MissionPlan {
    attribute waypoints : List<GPSCoordinate>;
  }

  datatype FlightCommands {
    attribute commands : List<String>;
  }

  datatype ObstacleAlert {
    attribute distance : Length;
  }

  datatype PayloadStatus {
    attribute payloadMass : Mass;
  }

  datatype BatteryStatus {
    attribute batteryChargePercent : Real;
  }

  datatype StatusUpdate {
    attribute position : GPSCoordinate;
  }

  datatype SafetyCommands {
    attribute command : String;
  }

  datatype FlightStatus {
    attribute airspeed : Velocity;
  }

  datatype RegulationData {}

  datatype GPSCoordinate {
    attribute latitude : Real;
    attribute longitude : Real;
  }

  // =========================
  // EXTERNAL SYSTEM
  // =========================

  systemdef GroundControl {
    port statusReceiver : in StatusUpdate;
    port missionSender : out MissionPlan;
  }

}