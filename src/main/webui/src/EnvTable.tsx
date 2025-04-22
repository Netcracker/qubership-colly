import React, {useEffect, useState} from "react";
import {Box, IconButton, InputAdornment} from "@mui/material";
import TextField from '@mui/material/TextField';
import SearchIcon from '@mui/icons-material/Search';
import EditIcon from '@mui/icons-material/Edit';
import {DataGrid} from '@mui/x-data-grid';
import EditEnvironmentDialog from "./components/EditEnvironmentDialog";
import {Environment} from "./entities/environments";


export default function EnvironmentsOverview() {
    const [filter, setFilter] = useState("");
    const [selectedEnv, setSelectedEnv] = useState<Environment | null>(null);
    const [environments, setEnvironments] = useState<Environment[]>([]);

    useEffect(() => {
        fetch("/colly/environments")
            .then(res => res.json())
            .then(data => setEnvironments(data))
            .catch(err => console.error("Failed to fetch environments:", err));
    }, []);


    const handleSave = async (changedEnv: Environment) => {
        if (!changedEnv) return;

        try {
            const formData = new FormData();
            formData.append("name", changedEnv.name);
            formData.append("owner", changedEnv.owner);
            formData.append("status", changedEnv.status);
            formData.append("description", changedEnv.description);
            const response = await fetch(`/colly/environments/${changedEnv.id}`, {
                method: "POST",
                body: formData
            });

            if (response.ok) {
                setSelectedEnv(null);
                setEnvironments(prev => prev.map(env => env.id === changedEnv.id ? changedEnv : env));
            } else {
                console.error("Failed to save changes", await response.text());
            }
        } catch (error) {
            console.error("Error during save:", error);
        }
    };

    const filteredRows = environments
        .filter(env => {
            const flatValues = [
                env.name,
                env.cluster?.name,
                env.owner,
                env.status,
                env.description,
                ...(env.namespaces || []).map(ns => ns.name)
            ].join(" ").toLowerCase();
            return flatValues.includes(filter.toLowerCase());
        })
        .map(env => ({
            id: env.id,
            name: env.name,
            namespaces: env.namespaces.map(ns => ns.name).join(", "),
            cluster: env.cluster?.name,
            owner: env.owner,
            status: env.status,
            description: env.description,
            raw: env
        }));

    const columns = [
        {field: "name", headerName: "Environment", flex: 1},
        {field: "namespaces", headerName: "Namespace(s)", flex: 1},
        {field: "cluster", headerName: "Cluster", flex: 1},
        {field: "owner", headerName: "Owner", flex: 1},
        {field: "status", headerName: "Status", flex: 1},
        {field: "description", headerName: "Description", flex: 2},
        {
            field: "actions",
            headerName: "Actions",
            sortable: false,
            filter: false,
            renderCell: (params: { row: { raw: React.SetStateAction<Environment | null>; }; }) => (
                <IconButton size={"small"} onClick={() => setSelectedEnv(params.row.raw)}>
                    <EditIcon fontSize="inherit"/>
                </IconButton>
            ),
            flex: 0.5
        }
    ];

    return (
        <Box sx={{p: 4}}>
            <Box sx={{display: 'flex', gap: 2, mb: 2, mx: 'auto', justifyContent: 'flex-start'}}>
                <TextField
                    id="filled-search"
                    label="Search Environment"
                    type="search"
                    size="small"
                    slotProps={{
                        input: {
                            startAdornment: (
                                <InputAdornment position="start">
                                    <SearchIcon />
                                </InputAdornment>
                            ),
                        },
                    }}
                    onChange={(e) => setFilter(e.target.value)}
                />
            </Box>

            <Box>
                <DataGrid
                    rows={filteredRows}
                    columns={columns}
                    disableRowSelectionOnClick
                    showToolbar
                />
            </Box>

            {selectedEnv && <EditEnvironmentDialog environment={selectedEnv} onSave={handleSave}
                                                   onClose={() => setSelectedEnv(null)}/>}
        </Box>
    );
}
