import EnvTable from "./EnvTable";
import {Typography} from "@mui/material";
import React from "react";


function App() {

    return (
        <div className="App">
            <Typography variant="h5" align="center">Environments Overview</Typography>
            <EnvTable/>
        </div>
    );
}

export default App;
