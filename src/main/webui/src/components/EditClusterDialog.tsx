import {Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField} from "@mui/material";
import React from "react";
import {Cluster} from "../entities/clusters";

type Props = {
    cluster: Cluster;
    onClose: () => void;
    onSave: (cluster: Cluster) => void;
};

export default function EditClusterDialog({cluster, onClose, onSave}: Props) {

    const [localCluster, setCluster] = React.useState<Cluster>(cluster);
    const handleSubmit = () => {
        onSave(localCluster);
    };

    return <Dialog open={!!localCluster} onClose={onClose} fullWidth={true} maxWidth="sm">
        <DialogTitle>Edit Cluster</DialogTitle>
        <DialogContent>
            <TextField
                label="Name"
                value={localCluster.name || ''}
                disabled
                fullWidth
                margin="dense"/>

            <TextField
                label="Description"
                value={localCluster.description || ''}
                onChange={e => setCluster(prev => ({...prev, description: e.target.value}))}
                fullWidth
                margin="dense"
            />
        </DialogContent>
        <DialogActions>
            <Button onClick={onClose} color="secondary">Close</Button>
            <Button onClick={handleSubmit} color="primary">Save Changes</Button>
        </DialogActions>
    </Dialog>
}
