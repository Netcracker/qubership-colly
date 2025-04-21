import React, {useEffect, useState} from "react";
import {
    Box,
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    InputAdornment,
    MenuItem,
    OutlinedInput,
    Select,
    TextField,
    Typography
} from "@mui/material";
import {DataGrid} from '@mui/x-data-grid';

type EnvironmentStatus = "IN_USE" | "RESERVED" | "FREE" | "MIGRATING";
const statuses: EnvironmentStatus[] = ["IN_USE", "RESERVED", "FREE", "MIGRATING"];
type Environment = {
    id: number;
    name: string;
    namespaces: { name: string }[];
    cluster: { name: string };
    owner: string;
    status: EnvironmentStatus;
    description: string;
};

export default function EnvironmentsOverview() {
    const [filter, setFilter] = useState("");
    const [statusFilter, setStatusFilter] = useState(() => new Set(statuses));
    const [selectedEnv, setSelectedEnv] = useState<Environment | null>(null);
    const [environments, setEnvironments] = useState<Environment[]>([]);

    useEffect(() => {
        fetch("/colly/environments")
            .then(res => res.json())
            .then(data => setEnvironments(data))
            .catch(err => console.error("Failed to fetch environments:", err));
    }, []);


    const handleSave = async () => {
        if (!selectedEnv) return;

        try {
            const formData = new FormData();
            formData.append("name", selectedEnv.name);
            formData.append("owner", selectedEnv.owner);
            formData.append("status", selectedEnv.status);
            formData.append("description", selectedEnv.description);
            const response = await fetch(`/colly/environments/${selectedEnv.id}`, {
                method: "POST",
                body: formData
                });


            if (response.ok) {
                setSelectedEnv(null);
                const updated = await response.json();
                setEnvironments(prev => prev.map(env => env.id === updated.id ? updated : env));
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
            <Typography variant="h5" gutterBottom align="center">Environments Overview</Typography>

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
                {statuses.map(status => (
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


            <Dialog open={!!selectedEnv} onClose={() => setSelectedEnv(null)}>
                <DialogTitle>Edit Environment</DialogTitle>
                <DialogContent sx={{display: 'flex', flexDirection: 'column', gap: 2, mt: 1}}>
                    <TextField label="Name" value={selectedEnv?.name || ''} disabled fullWidth/>
                    <TextField
                        label="Owner"
                        value={selectedEnv?.owner || ''}
                        onChange={e => setSelectedEnv(prev => prev ? {...prev, owner: e.target.value} : prev)}
                        fullWidth
                    />
                    <Select
                        value={selectedEnv?.status || ''}
                        onChange={e => setSelectedEnv(prev => prev ? {...prev, status: e.target.value as EnvironmentStatus} : prev)}
                        fullWidth
                    >
                        {statuses.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
                    </Select>
                    <TextField
                        label="Description"
                        value={selectedEnv?.description || ''}
                        onChange={e => setSelectedEnv(prev => prev ? {...prev, description: e.target.value} : prev)}
                        fullWidth
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSelectedEnv(null)} color="secondary">Close</Button>
                    <Button onClick={handleSave} color="primary">Save Changes</Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
