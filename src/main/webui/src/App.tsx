import React, {useEffect, useState} from "react";
import {Box, Tab, Tabs, Typography} from "@mui/material";
import EnvTable from "./components/EnvTable";
import ClustersTable from "./components/ClustersTable";
import LogoutButton from "./components/LogoutButton";
import {UserInfo} from "./entities/users";
import TabPanel from "./components/TabPanel";

function App() {
    const [value, setValue] = useState(0);
    const [userInfo, setUserInfo] = useState<UserInfo>({authenticated: false});

    useEffect(() => {
        fetch("/colly/auth-status")
            .then(res => res.json())
            .then(authData => {
                setUserInfo(authData);
            })
            .catch(err => {
                console.error("Failed to fetch auth status:", err);
            })
    }, []);

    const handleChange = (event: React.SyntheticEvent, newValue: number) => {
        setValue(newValue);
    };

    return (
        <div className="App">
            <Box sx={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2, px: 2}}>
                <Typography variant="h5" align="center" sx={{flex: 1}}>
                    Infrastructure Overview
                </Typography>
                {userInfo.authenticated && (
                    <LogoutButton displayedName={userInfo.username}/>
                )}
            </Box>

            <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
                <Tabs value={value} onChange={handleChange} aria-label="colly tabs">
                    <Tab label="Environments" id={"envs"}/>
                    <Tab label="Clusters" id={"clusters"}/>
                </Tabs>
            </Box>

            <TabPanel value={value} index={0}>
                <EnvTable userInfo={userInfo}/>
            </TabPanel>

            <TabPanel value={value} index={1}>
                <ClustersTable userInfo={userInfo}/>
            </TabPanel>
        </div>
    );
}

export default App;
