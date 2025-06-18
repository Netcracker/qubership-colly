import React, {useEffect, useState} from "react";
import {Box, Chip, IconButton} from "@mui/material";
import EditIcon from '@mui/icons-material/Edit';
import {DataGrid, GridColDef} from '@mui/x-data-grid';
import EditEnvironmentDialog from "./EditEnvironmentDialog";
import {Environment, ENVIRONMENT_TYPES_MAPPING, STATUS_MAPPING} from "../entities/environments";
import {UserInfo} from "../entities/users";
import dayjs from "dayjs";

interface EnvTableProps {
    userInfo: UserInfo;
}

export default function EnvTable({userInfo}: EnvTableProps) {
    const [selectedEnv, setSelectedEnv] = useState<Environment | null>(null);
    const [environments, setEnvironments] = useState<Environment[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetch("/colly/environments").then(res => res.json())
            .then(envData => setEnvironments(envData))
            .catch(err => console.error("Failed to fetch environments:", err))
            .finally(() => setLoading(false));
    }, []);


    const handleSave = async (changedEnv: Environment) => {
        if (!changedEnv) return;

        try {
            const formData = new FormData();
            if (changedEnv.owner) {
                formData.append("owner", changedEnv.owner);
            }
            if (changedEnv.team) {
                formData.append("team", changedEnv.team);
            }
            if (changedEnv.description) {
                formData.append("description", changedEnv.description);
            }
            formData.append("status", changedEnv.status);
            formData.append("type", changedEnv.type);
            formData.append("name", changedEnv.name);
            formData.append("expirationDate", changedEnv.expirationDate ? dayjs(changedEnv.expirationDate).format("YYYY-MM-DD") : "");
            changedEnv.labels.forEach(label => formData.append("labels", label));

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

    const rows = environments.map(env => ({
        id: env.id,
        name: env.name,
        namespaces: env.namespaces.map(ns => ns.name).join(", "),
        cluster: env.cluster?.name,
        owner: env.owner,
        team: env.team,
        status: STATUS_MAPPING[env.status] || env.status,
        expirationDate: env.expirationDate,
        type: ENVIRONMENT_TYPES_MAPPING[env.type] || env.type,
        labels: env.labels,
        description: env.description,
        deploymentVersion: env.deploymentVersion,
        ...(env.monitoringData || {}),
        raw: env
    }));

    const monitoringKeys = environments.length > 0 && environments[0].monitoringData
        ? Object.keys(environments[0].monitoringData)
        : [];

    const monitoringColumns: GridColDef[] = monitoringKeys.map(key => ({
        field: key,
        headerName: key,
        flex: 0.8,
        type: 'string'
    }));

    const baseColumns: GridColDef[] = [
        {field: "name", headerName: "Name", flex: 1},
        {field: "type", headerName: "Type", flex: 1},
        {field: "namespaces", headerName: "Namespace(s)", flex: 1},
        {field: "cluster", headerName: "Cluster", flex: 1},
        {field: "owner", headerName: "Owner", flex: 1},
        {field: "team", headerName: "Team", flex: 1},
        {field: "expirationDate", headerName: "Expiration Date",
            valueFormatter: (value?: string) => {
                if (value == null) {
                    return '';
                }
                return new Date(value).toLocaleDateString();
            },
            flex: 1},
        {field: "status", headerName: "Status", flex: 1},
        {
            field: "labels", headerName: "Labels", flex: 1,
            renderCell: (params: { row: { labels: string[]; }; }) =>
                <>
                    {params.row.labels.map(label => <Chip label={label} key={label}/>)}
                </>
        },
        {field: "description", headerName: "Description", flex: 2},
        {field: "deploymentVersion", headerName: "Version", flex: 2}
    ];
    const actionsColumn: GridColDef = {
        field: "actions",
        headerName: "Actions",
        sortable: false,
        filterable: false,
        renderCell: (params: { row: { raw: React.SetStateAction<Environment | null>; }; }) => (
            <IconButton size={"small"} onClick={() => setSelectedEnv(params.row.raw)}>
                <EditIcon fontSize="inherit"/>
            </IconButton>
        ),
        flex: 0.5
    };

    const columns: GridColDef[] = [
        ...baseColumns,
        ...monitoringColumns,
        ...(userInfo.authenticated && userInfo.isAdmin ? [actionsColumn] : [])
    ];

    if (loading) {
        return <Box sx={{p: 4}}>Loading...</Box>;
    }

    return (
        <Box>
            <Box>
                <DataGrid
                    rows={rows}
                    columns={columns}
                    disableRowSelectionOnClick
                    showToolbar
                />
            </Box>

            {selectedEnv && userInfo.authenticated && userInfo.isAdmin && (
                <EditEnvironmentDialog
                    environment={selectedEnv}
                    allLabels={Array.from(new Set(environments.flatMap(env => env.labels)))}
                    onSave={handleSave}
                    onClose={() => setSelectedEnv(null)}
                />
            )}
        </Box>
    );
}
