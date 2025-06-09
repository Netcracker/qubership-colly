import React, { useState } from "react";
import { Typography, Tabs, Tab, Box } from "@mui/material";
import EnvTable from "./components/EnvTable";
import ClustersTable from "./components/ClustersTable";

interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

function TabPanel(props: TabPanelProps) {
    const { children, value, index, ...other } = props;

    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`simple-tabpanel-${index}`}
            aria-labelledby={`simple-tab-${index}`}
            {...other}
        >
            {value === index && (
                <Box sx={{ p: 3 }}>
                    {children}
                </Box>
            )}
        </div>
    );
}

function a11yProps(index: number) {
    return {
        id: `simple-tab-${index}`,
        'aria-controls': `simple-tabpanel-${index}`,
    };
}

function App() {
    const [value, setValue] = useState(0);

    const handleChange = (event: React.SyntheticEvent, newValue: number) => {
        setValue(newValue);
    };

    return (
        <div className="App">
            <Typography variant="h5" align="center" sx={{ mb: 2 }}>
                Infrastructure Overview
            </Typography>

            <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
                <Tabs value={value} onChange={handleChange} aria-label="infrastructure tabs">
                    <Tab label="Environments" {...a11yProps(0)} />
                    <Tab label="Clusters" {...a11yProps(1)} />
                </Tabs>
            </Box>

            <TabPanel value={value} index={0}>
                <EnvTable />
            </TabPanel>

            <TabPanel value={value} index={1}>
                <ClustersTable />
            </TabPanel>
        </div>
    );
}

export default App;
