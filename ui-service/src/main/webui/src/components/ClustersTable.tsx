import React, {useEffect, useState} from "react";
import {Box, Chip} from "@mui/material";
import {DataGrid, GridColDef} from '@mui/x-data-grid';
import {UserInfo} from "../entities/users";
import {Cluster} from "../entities/clusters";
import EditClusterDialog from "./EditClusterDialog";
import ClusterTableToolbar from "./ClusterTableToolbar";

interface ClusterTableProps {
    userInfo: UserInfo;
}

export default function ClustersTable({userInfo}: ClusterTableProps) {
    const [selectedCluster, setSelectedCluster] = useState<Cluster | null>(null);
    const [clusters, setClusters] = useState<Cluster[]>([]);
    const [loading, setLoading] = useState(true);
    const [showEditDialog, setShowEditDialog] = useState(false);


    useEffect(() => {
        fetch("/colly/v2/ui-service/clusters")
            .then(res => res.json())
            .then(clustersData => setClusters(clustersData))
            .catch(err => console.error("Failed to fetch clusters:", err))
            .finally(() => setLoading(false));
    }, []);

    const handleSave = async (changedCluster: Cluster) => {
        if (!changedCluster) return;

        try {
            const payload = {
                name: changedCluster.name,
                description: changedCluster.description || null
            };

            const response = await fetch(`/colly/v2/ui-service/clusters/${changedCluster.name}`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
            });

            if (response.ok) {
                setShowEditDialog(false);
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
        return (<ClusterTableToolbar
                userInfo={userInfo}
                isEditEnabled={!selectedCluster}
                onEditClick={() => setShowEditDialog(true)}
            />
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
                            const selectedClusterId = rowSelectionModel.ids.keys().next().value;
                            const cluster = clusters.find(e => e.name === selectedClusterId);
                            if (cluster) {
                                setSelectedCluster(cluster);
                            } else setSelectedCluster(null);
                        } else {
                            setSelectedCluster(null);
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
                    show={showEditDialog}
                    onSave={handleSave}
                    onClose={() => setShowEditDialog(false)}
                />
            )}
        </Box>
    );
}
