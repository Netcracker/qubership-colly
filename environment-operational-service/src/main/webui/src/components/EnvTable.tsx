import React, {useCallback, useEffect, useMemo, useState} from "react";
import {Box, Chip} from "@mui/material";
import {DataGrid, GridColDef, useGridApiRef} from '@mui/x-data-grid';
import {
    DEPLOYMENT_STATUS_MAPPING, DeploymentStatus,
    Environment,
    ENVIRONMENT_TYPES_MAPPING,
    EnvironmentStatus,
    STATUS_MAPPING
} from "../entities/environments";
import {UserInfo} from "../entities/users";
import dayjs from "dayjs";
import ConfirmationDialog from "./ConfirmDialog";
import EditEnvironmentDialog from "./EditEnvironmentDialog";
import EnvTableToolbar from "./EnvTableToolbar";
import {Namespace} from "../entities/namespaces";

interface EnvTableProps {
    userInfo: UserInfo;
    monitoringColumns: string[];
}

const STORAGE_KEY = 'env-table-state';

export default function EnvTable({userInfo, monitoringColumns}: EnvTableProps) {
    const [environments, setEnvironments] = useState<Environment[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedEnvironment, setSelectedEnvironment] = useState<Environment | null>(null);
    const [showConfirmPopup, setShowConfirmPopup] = useState(false);
    const [showEditDialog, setShowEditDialog] = useState(false);
    const [showAllNamespaces, setShowAllNamespaces] = useState(false);


    const apiRef = useGridApiRef();
    const [isInitialized, setIsInitialized] = useState(false);

    useEffect(() => {
        fetch("/colly/environment-operational-service/environments").then(res => res.json())
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

                    if (state.showAllNamespaces !== undefined) {
                        setShowAllNamespaces(state.showAllNamespaces);
                    }
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
                focus: undefined,
                filterModel: {
                    filterPanelState: undefined
                },
                preferencePanel: undefined,
                showAllNamespaces: showAllNamespaces
            };

            localStorage.setItem(STORAGE_KEY, JSON.stringify(stateToSave));
        } catch (error) {
            console.error("Failed to save DataGrid state:", error);
        }
    }, [isInitialized, apiRef, showAllNamespaces]);

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

    useEffect(() => {
        if (isInitialized) {
            saveState();
        }
    }, [showAllNamespaces, saveState, isInitialized]);

    const handleDeleteAction = useCallback(async () => {
        try {
            if (!selectedEnvironment) {
                console.error("selected env is null")
                return;
            }
            const response = await fetch(`/colly/environment-operational-service/environments/${selectedEnvironment.id}`, {
                method: "DELETE",
            });

            if (response.ok) {
                setSelectedEnvironment(null)
                setShowConfirmPopup(false)
                setEnvironments(prev => prev.filter(env => env.id !== selectedEnvironment.id));
            } else {
                console.error("Failed to delete environment", await response.text());
            }
        } catch (error) {
            console.error("Error during delete environment:", error);
        }
    }, [selectedEnvironment]);

    const allLabels = useMemo(() => {
        return Array.from(new Set(environments.flatMap(env => env.labels)));
    }, [environments]);

    const handleSaveAction = useCallback(async (changedEnv: Environment) => {
        try {
            const formData = new FormData();
            if (changedEnv.owners) {
                changedEnv.owners.forEach(owner => formData.append("owners", owner));
            }
            if (changedEnv.team) {
                formData.append("team", changedEnv.team);
            }
            if (changedEnv.description) {
                formData.append("description", changedEnv.description);
            }
            if (changedEnv.ticketLinks) {
                formData.append("ticketLinks", changedEnv.ticketLinks);
            }
            formData.append("status", changedEnv.status);
            formData.append("type", changedEnv.type);
            formData.append("name", changedEnv.name);
            formData.append("deploymentStatus", changedEnv.deploymentStatus);

            formData.append("expirationDate", changedEnv.expirationDate ? dayjs(changedEnv.expirationDate).format("YYYY-MM-DD") : "");
            changedEnv.labels.forEach(label => formData.append("labels", label));

            const response = await fetch(`/colly/environment-operational-service/environments/${changedEnv.id}`, {
                method: "POST",
                body: formData
            });

            if (response.ok) {
                setShowEditDialog(false);
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
        namespaces: env.namespaces.filter((namespace: Namespace) => showAllNamespaces || namespace.existsInK8s),
        cluster: env.cluster?.name,
        owners: env.owners,
        team: env.team,
        status: env.status,
        expirationDate: env.expirationDate,
        type: ENVIRONMENT_TYPES_MAPPING[env.type] || env.type,
        labels: env.labels,
        description: env.description,
        deploymentVersion: env.deploymentVersion,
        cleanInstallationDate: env.cleanInstallationDate,
        deploymentStatus: env.deploymentStatus,
        ticketLinks: env.ticketLinks,
        ...(env.monitoringData || {}),
        raw: env
    })), [environments, showAllNamespaces]);

    const columns = useMemo(() => {
        const monitoringCols: GridColDef[] = monitoringColumns.map(key => ({
            field: key,
            headerName: key,
            width: 150,
            type: 'string'
        }));

        const baseColumns: GridColDef[] = [
            {field: "name", headerName: "Name", width: 150},
            {field: "type", headerName: "Type", width: 120},
            {
                field: "namespaces", headerName: "Namespace(s)", width: 200,
                renderCell: (params) => (
                    <Box sx={{display: 'flex', flexDirection: 'column'}}>
                        {params.row.namespaces
                            .map((namespace: Namespace) => (
                                <Box key={namespace.name}
                                     sx={{color: namespace.existsInK8s ? "default" : "error.main"}}>{namespace.name}</Box>
                            ))}
                    </Box>
                )
            },
            {field: "cluster", headerName: "Cluster", width: 150},
            {
                field: "owners", headerName: "Owner(s)", width: 120,
                valueFormatter: (value: string[]) => {
                    if (value == null) {
                        return '';
                    }
                    return value.join(', ');
                }
            },
            {field: "team", headerName: "Team", width: 120},
            {
                field: "expirationDate", headerName: "Expiration Date",
                valueFormatter: (value?: string) => {
                    if (value == null) {
                        return '';
                    }
                    return new Date(value).toLocaleDateString();
                },
                width: 150
            },
            {
                field: "status", headerName: "Status", width: 120,
                renderCell: (params: { row: { status: EnvironmentStatus; }; }) =>
                    <Chip label={STATUS_MAPPING[params.row.status]} size={"small"}
                          color={calculateStatusColor(params.row.status)}
                    />
            },
            {
                field: "labels", headerName: "Labels", width: 200,
                renderCell: (params: { row: { labels: string[]; }; }) =>
                    <>
                        {params.row.labels.map(label => <Chip size={"small"} label={label} key={label}/>)}
                    </>
            },
            {field: "description", headerName: "Description", width: 300},
            {field: "deploymentStatus", headerName: "Deployment Status",
                renderCell: (params: { row: { deploymentStatus: DeploymentStatus; }; }) => {
                    if (params.row.deploymentStatus == null) {
                        return '';
                    }
                    return DEPLOYMENT_STATUS_MAPPING[params.row.deploymentStatus];
                },
                width: 150},
            {field: "ticketLinks", headerName: "Linked Tickets", width: 150},
            {field: "deploymentVersion", headerName: "Version", width: 150},
            {
                field: "cleanInstallationDate", headerName: "Clean Installation Date",
                valueFormatter: (value?: string) => {
                    if (value == null) {
                        return '';
                    }
                    return new Date(value).toLocaleString();
                },
                width: 200
            }
        ];

        return [
            ...baseColumns,
            ...monitoringCols
        ];
    }, [monitoringColumns]);

    const CustomToolbar = () => {
        return (
            <EnvTableToolbar
                userInfo={userInfo}
                selectedEnvironment={selectedEnvironment}
                showAllNamespaces={showAllNamespaces}
                onShowEditDialog={() => setShowEditDialog(true)}
                onShowConfirmPopup={() => setShowConfirmPopup(true)}
                onShowAllNamespacesChange={setShowAllNamespaces}
            />
        );
    };

    if (loading) {
        return <Box sx={{p: 4}}>Loading...</Box>;
    }

    return (
        <Box>
            <Box sx={{width: '100%', overflowX: 'auto'}}>
                <DataGrid
                    apiRef={apiRef}
                    rows={rows}
                    columns={columns}
                    rowSelection={true}
                    getRowHeight={() => 'auto'}
                    sx={{
                        minWidth: 800,
                        '& .MuiDataGrid-columnHeaderTitle': {
                            fontWeight: 'bold'
                        },
                        '& .MuiDataGrid-cell': {
                            display: 'flex',
                            alignItems: 'center'
                        }
                    }}
                    slots={{
                        toolbar: CustomToolbar
                    }}
                    showToolbar
                    checkboxSelection
                    disableMultipleRowSelection
                    onRowSelectionModelChange={(rowSelectionModel) => {
                        if (rowSelectionModel.ids.size > 0) {
                            const selectedId = rowSelectionModel.ids.keys().next().value;
                            let environment = environments.find(e => e.id === selectedId);
                            if (environment) {
                                setSelectedEnvironment(environment);
                            } else {
                                setSelectedEnvironment(null)
                            }
                        } else {
                            setSelectedEnvironment(null);
                        }
                    }
                    }
                />
            </Box>

            {selectedEnvironment && userInfo.authenticated && userInfo.isAdmin && (<EditEnvironmentDialog
                show={showEditDialog}
                environment={selectedEnvironment}
                allLabels={allLabels}
                onSave={handleSaveAction}
                onClose={() => setShowEditDialog(false)}
            />)}

            <ConfirmationDialog open={showConfirmPopup}
                                title={"Delete Environment"}
                                content={"Are you sure you want to permanently delete the environment: " + selectedEnvironment?.name + ". All data will be lost and cannot be recovered."}
                                onClose={() => setShowConfirmPopup(false)}
                                onConfirm={handleDeleteAction}/>
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

