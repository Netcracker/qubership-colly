import React, {useCallback, useEffect, useMemo, useState} from "react";
import {Box, Chip, IconButton} from "@mui/material";
import EditIcon from '@mui/icons-material/Edit';
import {DataGrid, GridColDef, useGridApiRef} from '@mui/x-data-grid';
import EditEnvironmentDialog from "./EditEnvironmentDialog";
import {Environment, ENVIRONMENT_TYPES_MAPPING, EnvironmentStatus, STATUS_MAPPING} from "../entities/environments";
import {UserInfo} from "../entities/users";
import dayjs from "dayjs";

interface EnvTableProps {
    userInfo: UserInfo;
    monitoringColumns: string[];
}

const STORAGE_KEY = 'env-table-state';

export default function EnvTable({userInfo, monitoringColumns}: EnvTableProps) {
    const [selectedEnv, setSelectedEnv] = useState<Environment | null>(null);
    const [environments, setEnvironments] = useState<Environment[]>([]);
    const [loading, setLoading] = useState(true);

    const apiRef = useGridApiRef();
    const [isInitialized, setIsInitialized] = useState(false);

    useEffect(() => {
        fetch("/colly/environments").then(res => res.json())
            .then(envData => setEnvironments(envData))
            .catch(err => console.error("Failed to fetch environments:", err))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!loading && apiRef.current && environments.length > 0) {
            try {
                const savedState = localStorage.getItem(STORAGE_KEY);
                if (savedState) {
                    const state = JSON.parse(savedState);
                    apiRef.current.restoreState(state);
                }
            } catch (error) {
                console.error("Failed to load DataGrid state:", error);
            } finally {
                setTimeout(() => setIsInitialized(true), 100);
            }
        }
    }, [loading, environments, apiRef]);

    const saveState = useCallback(() => {
        if (!isInitialized || !apiRef.current) return;
        try {
            const state = apiRef.current.exportState();
            const stateToSave = {
                ...state,
                rowSelection: undefined,
                focus: undefined
            };

            localStorage.setItem(STORAGE_KEY, JSON.stringify(stateToSave));
        } catch (error) {
            console.error("Failed to save DataGrid state:", error);
        }
    }, [isInitialized, apiRef]);

    useEffect(() => {
        if (!apiRef.current || !isInitialized) return;

        const unsubscribers = [
            apiRef.current.subscribeEvent('columnVisibilityModelChange', saveState),
            apiRef.current.subscribeEvent('columnWidthChange', saveState),
            apiRef.current.subscribeEvent('filterModelChange', saveState),
            apiRef.current.subscribeEvent('sortModelChange', saveState),
            apiRef.current.subscribeEvent('paginationModelChange', saveState),
        ];

        return () => {
            unsubscribers.forEach(unsubscribe => unsubscribe());
        };
    }, [saveState, apiRef, isInitialized]);

    const handleEditClick = useCallback((env: Environment) => {
        setSelectedEnv(env);
    }, []);

    const allLabels = useMemo(() => {
        return Array.from(new Set(environments.flatMap(env => env.labels)));
    }, [environments]);

    const handleSave = useCallback(async (changedEnv: Environment) => {
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
    }, []);

    const rows = useMemo(() => environments.map(env => ({
        id: env.id,
        name: env.name,
        namespaces: env.namespaces.map(ns => ns.name).join(", "),
        cluster: env.cluster?.name,
        owner: env.owner,
        team: env.team,
        status: env.status,
        expirationDate: env.expirationDate,
        type: ENVIRONMENT_TYPES_MAPPING[env.type] || env.type,
        labels: env.labels,
        description: env.description,
        deploymentVersion: env.deploymentVersion,
        cleanInstallationDate: env.cleanInstallationDate,
        ...(env.monitoringData || {}),
        raw: env
    })), [environments]);

    const columns = useMemo(() => {
        const monitoringCols: GridColDef[] = monitoringColumns.map(key => ({
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
            {
                field: "expirationDate", headerName: "Expiration Date",
                valueFormatter: (value?: string) => {
                    if (value == null) {
                        return '';
                    }
                    return new Date(value).toLocaleDateString();
                },
                flex: 1
            },
            {
                field: "status", headerName: "Status", flex: 1,
                renderCell: (params: { row: { status: EnvironmentStatus; }; }) =>
                    <Chip label={STATUS_MAPPING[params.row.status]} size={"small"} color={calculateStatusColor(params.row.status)}
                    />
            },
            {
                field: "labels", headerName: "Labels", flex: 1,
                renderCell: (params: { row: { labels: string[]; }; }) =>
                    <>
                        {params.row.labels.map(label => <Chip size={"small"} label={label} key={label}/>)}
                    </>
            },
            {field: "description", headerName: "Description", flex: 2},
            {field: "deploymentVersion", headerName: "Version", flex: 2},
            {
                field: "cleanInstallationDate", headerName: "Clean Installation Date",
                valueFormatter: (value?: string) => {
                    if (value == null) {
                        return '';
                    }
                    return new Date(value).toLocaleString();
                },
            }
        ];

        const actionsColumn: GridColDef = {
            field: "actions",
            headerName: "Actions",
            sortable: false,
            filterable: false,
            renderCell: (params: { row: { raw: Environment; }; }) => (
                <IconButton size={"small"} onClick={() => handleEditClick(params.row.raw)}>
                    <EditIcon fontSize="inherit"/>
                </IconButton>
            ),
            flex: 0.5
        };

        return [
            ...baseColumns,
            ...monitoringCols,
            ...(userInfo.authenticated && userInfo.isAdmin ? [actionsColumn] : [])
        ];
    }, [monitoringColumns, userInfo.authenticated, userInfo.isAdmin, handleEditClick]);

    if (loading) {
        return <Box sx={{p: 4}}>Loading...</Box>;
    }

    return (
        <Box>
            <Box>
                <DataGrid
                    apiRef={apiRef}
                    rows={rows}
                    columns={columns}
                    disableRowSelectionOnClick
                    showToolbar
                />
            </Box>

            {selectedEnv && userInfo.authenticated && userInfo.isAdmin && (
                <EditEnvironmentDialog
                    environment={selectedEnv}
                    allLabels={allLabels}
                    onSave={handleSave}
                    onClose={() => setSelectedEnv(null)}
                />
            )}
        </Box>
    );
}
function calculateStatusColor(status: EnvironmentStatus): "success" | "primary" | "warning" | "error" | "default" {
    switch (status) {
        case "IN_USE":
            return "success";
        case "FREE":
            return "primary";
        case "MIGRATING":
            return "warning";
        case "RESERVED":
            return "error";
        default:
            return "default";
    }
}

