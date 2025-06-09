import React, {useEffect, useState} from "react";
import {Box, IconButton} from "@mui/material";
import {DataGrid, GridColDef} from '@mui/x-data-grid';
import {UserInfo} from "../entities/users";
import {Cluster} from "../entities/clusters";
import EditIcon from "@mui/icons-material/Edit";
import EditClusterDialog from "./EditClusterDialog";

interface ClusterTableProps {
    userInfo: UserInfo;
}

export default function ClustersTable({userInfo}: ClusterTableProps) {
    const [selectedCluster, setSelectedCluster] = useState<Cluster | null>(null);
    const [clusters, setClusters] = useState<Cluster[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetch("/colly/clusters")
            .then(res => res.json())
            .then(clustersData => setClusters(clustersData))
            .catch(err => console.error("Failed to fetch clusters:", err))
            .finally(() => setLoading(false));
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

    const actionsColumn: GridColDef = {
        field: "actions",
        headerName: "Actions",
        sortable: false,
        filterable: false,
        renderCell: (params: { row: { raw: React.SetStateAction<Cluster | null>; }; }) => (
            <IconButton size={"small"} onClick={() => {
                console.log("ffff");
                setSelectedCluster(params.row.raw)
            }}>
                <EditIcon fontSize="inherit"/>
            </IconButton>
        ),
        flex: 0.5
    };
    const baseColumns: GridColDef[] = [
        {
            field: "name",
            headerName: "Name",
            flex: 1
        },
        {
            field: "description",
            headerName: "Description",
            flex: 2
        }
    ];

    const columns: GridColDef[] = [
        ...baseColumns,
        ...(userInfo.authenticated && userInfo.isAdmin ? [actionsColumn] : [])
    ];


    const rows = clusters.map(cluster => ({
        id: cluster.name,
        name: cluster.name,
        description: cluster.description || '',
        raw: cluster
    }));

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
