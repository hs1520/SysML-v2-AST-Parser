package Requirements {
    requirement def PerformanceReq {
        subject vehicle : Vehicle;
        require constraint { vehicle.speed >= 100.0 }
    }
    
    part def Vehicle {
        attribute speed : Real;
    }
    
    part myCar : Vehicle {
        satisfy PerformanceReq;
    }
}
