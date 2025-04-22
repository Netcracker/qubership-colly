import React, {useEffect, useState} from "react";
import {Box, Button, Checkbox, FormControlLabel, InputAdornment, OutlinedInput} from "@mui/material";
import {DataGrid} from '@mui/x-data-grid';
import EditEnvironmentDialog from "./components/EditEnvironmentDialog";
import {ALL_STATUSES, Environment, EnvironmentStatus} from "./entities/environments";


export default function EnvironmentsOverview() {
    const [filter, setFilter] = useState("");
    const [statusFilter, setStatusFilter] = useState(() => new Set(ALL_STATUSES));
    const [selectedEnv, setSelectedEnv] = useState<Environment | null>(null);
    const [environments, setEnvironments] = useState<Environment[]>([]);

    useEffect(() => {
        fetch("/colly/environments")
            .then(res => res.json())
            .then(data => setEnvironments(data))
            .catch(err => console.error("Failed to fetch environments:", err));
    }, []);


    const handleSave = async (changedEnv:Environment) => {
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

    const toggleStatus = (status: EnvironmentStatus) => {
        setStatusFilter(prev => {
            const newSet = new Set(prev);
            newSet.has(status) ? newSet.delete(status) : newSet.add(status);
            return newSet;
        });
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
            return flatValues.includes(filter.toLowerCase()) && statusFilter.has(env.status);
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
            renderCell: (params: { row: { raw: React.SetStateAction<Environment | null>; }; }) => (
                <Button variant="outlined" onClick={() => setSelectedEnv(params.row.raw)}>✏️</Button>
            ),
            flex: 0.5
        }
    ];

    return (
        <Box sx={{p: 4}}>
            <Box sx={{display: 'flex', gap: 2, mb: 2, maxWidth: 500, mx: 'auto'}}>
                <OutlinedInput
                    fullWidth
                    placeholder="Search Environment..."
                    value={filter}
                    onChange={(e) => setFilter(e.target.value)}
                    startAdornment={<InputAdornment position="start">Search</InputAdornment>}
                />
                <Button variant="outlined" onClick={() => setFilter("")}>Clear</Button>
            </Box>

            <Box sx={{display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center', mb: 3}}>
                {ALL_STATUSES.map(status => (
                    <FormControlLabel
                        key={status}
                        control={<Checkbox checked={statusFilter.has(status)} onChange={() => toggleStatus(status)}/>}
                        label={status}
                    />
                ))}
            </Box>

            <Box sx={{height: 500}}>
                <DataGrid
                    rows={filteredRows}
                    columns={columns}
                    disableRowSelectionOnClick
                />
            </Box>

            {selectedEnv && <EditEnvironmentDialog environment={selectedEnv} onSave={handleSave}
                                                   onClose={() => setSelectedEnv(null)}/>}
        </Box>
    );
}
