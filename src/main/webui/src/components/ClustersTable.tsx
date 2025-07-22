import React, {useCallback, useEffect, useState} from "react";
import {Box, Chip, InputAdornment, TextField, Tooltip} from "@mui/material";
import {
    DataGrid,
    GridColDef,
    QuickFilter,
    QuickFilterClear,
    QuickFilterControl,
    Toolbar,
    ToolbarButton
} from '@mui/x-data-grid';
import {UserInfo} from "../entities/users";
import {Cluster} from "../entities/clusters";
import EditIcon from "@mui/icons-material/Edit";
import EditClusterDialog from "./EditClusterDialog";
import SearchIcon from "@mui/icons-material/Search";
import CancelIcon from "@mui/icons-material/Cancel";

interface ClusterTableProps {
    userInfo: UserInfo;
}

export default function ClustersTable({userInfo}: ClusterTableProps) {
    const [selectedCluster, setSelectedCluster] = useState<Cluster | null>(null);
    const [clusters, setClusters] = useState<Cluster[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedClusterId, setSelectedClusterId] = useState<string | null>(null);


    useEffect(() => {
        fetch("/colly/clusters")
            .then(res => res.json())
            .then(clustersData => setClusters(clustersData))
            .catch(err => console.error("Failed to fetch clusters:", err))
            .finally(() => setLoading(false));
    }, []);

    const handleEditClick = useCallback((cluster: Cluster) => {
        setSelectedCluster(cluster);
    }, []);


    const handleSave = async (changedCluster: Cluster) => {
        if (!changedCluster) return;

        try {
            const formData = new FormData();
            if (changedCluster.description) {
                formData.append("description", changedCluster.description);
            }
            formData.append("name", changedCluster.name);

            const response = await fetch(`/colly/clusters/${changedCluster.name}`, {
                method: "POST",
                body: formData
            });

            if (response.ok) {
                setSelectedCluster(null);
                setClusters(prev => prev.map(cluster => cluster.name === changedCluster.name ? changedCluster : cluster));
            } else {
                console.error("Failed to save changes", await response.text());
            }
        } catch (error) {
            console.error("Error during save:", error);
        }
    };

    const columns: GridColDef[] = [
        {
            field: "name",
            headerName: "Name",
            flex: 1
        },
        {
            field: "description",
            headerName: "Description",
            flex: 2
        },
        {
            field: "synced",
            headerName: "Sync Status",
            flex: 1,
            renderCell: (params: { row: { synced: boolean; }; }) =>
                <Chip label={params.row.synced ? "Synced" : "Not Synced"} size={"small"}
                      color={params.row.synced ? "success" : "error"}
                />
        }
    ];

    const rows = clusters.map(cluster => ({
        id: cluster.name,
        name: cluster.name,
        description: cluster.description || '',
        synced: cluster.synced,
        raw: cluster
    }));
    const CustomToolbar = () => {
        return (
            <Toolbar>
                {userInfo.authenticated && userInfo.isAdmin && (
                    <Tooltip title="Edit">
                        <ToolbarButton
                            size="medium"
                            onClick={() => {
                                const cluster = clusters.find(e => e.name === selectedClusterId);
                                if (cluster) handleEditClick(cluster);
                            }}
                            disabled={!selectedClusterId}
                        >
                            <EditIcon fontSize="small"/>
                        </ToolbarButton>
                    </Tooltip>
                )}

                <QuickFilter>
                    <QuickFilterControl
                        render={({ref, ...controlProps}, state) => (
                            <TextField
                                {...controlProps}
                                inputRef={ref}
                                aria-label="Search"
                                placeholder="Search..."
                                size="small"
                                slotProps={{
                                    input: {
                                        startAdornment: (
                                            <InputAdornment position="start">
                                                <SearchIcon fontSize="small"/>
                                            </InputAdornment>
                                        ),
                                        endAdornment: state.value ? (
                                            <InputAdornment position="end">
                                                <QuickFilterClear
                                                    edge="end"
                                                    size="small"
                                                    aria-label="Clear search"
                                                >
                                                    <CancelIcon fontSize="small"/>
                                                </QuickFilterClear>
                                            </InputAdornment>
                                        ) : null,
                                        ...controlProps.slotProps?.input,
                                    },
                                    ...controlProps.slotProps,
                                }}
                            />
                        )}
                    />
                </QuickFilter>
            </Toolbar>
        )
    };


    if (loading) {
        return <Box sx={{p: 4}}>Loading...</Box>;
    }

    return (
        <Box>
            <Box>
                <DataGrid
                    rows={rows}
                    columns={columns}
                    checkboxSelection
                    disableMultipleRowSelection
                    hideFooter={false}
                    disableColumnMenu
                    onRowSelectionModelChange={(rowSelectionModel) => {
                        if (rowSelectionModel.ids.size > 0) {
                            console.log(rowSelectionModel)
                            const selectedClusterId = rowSelectionModel.ids.keys().next().value;
                            setSelectedClusterId(selectedClusterId);
                        } else {
                            setSelectedClusterId(null);
                        }
                    }}
                    slots={{
                        toolbar: CustomToolbar,
                    }}
                    showToolbar
                    sx={{
                        '& .MuiDataGrid-columnHeaderTitle': {
                            fontWeight: 'bold'
                        }
                    }}
                />
            </Box>
            {selectedCluster && userInfo.authenticated && userInfo.isAdmin && (
                <EditClusterDialog
                    cluster={selectedCluster}
                    onSave={handleSave}
                    onClose={() => setSelectedCluster(null)}
                />
            )}
        </Box>
    );
}
