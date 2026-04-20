package VehicleDesign {
    part def Vehicle {
        attribute mass : Real;
        port enginePort : EngineIF;
        
        action def Start {
            in attribute command : String;
            out attribute status : String;
        }
    }
    
    part def Engine {
        port output : EngineIF;
        attribute power : Real;
    }
    
    port def EngineIF {
        attribute torque : Real;
        attribute rpm : Real;
    }
    
    requirement def PerformanceReq {
        subject v : Vehicle;
        require constraint { v.mass < 2000.0 }
    }
    
    part myCar : Vehicle {
        part engine : Engine;
        connect myCar.enginePort to engine.output;
        satisfy PerformanceReq;
    }
}
