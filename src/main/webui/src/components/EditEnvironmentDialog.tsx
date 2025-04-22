import {Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Select, TextField} from "@mui/material";
import React from "react";
import {ALL_STATUSES, Environment, EnvironmentStatus} from "../entities/environments";

type Props = {
    environment: Environment;
    onClose: () => void;
    onSave: (env: Environment) => void;
};

export default function EditEnvironmentDialog({environment, onClose, onSave}: Props) {

    const [localEnv, setLocalEnv] = React.useState<Environment>(environment);
    const handleSubmit = () => {
        onSave(localEnv);
    };

    return <Dialog open={!!localEnv} onClose={onClose}>
        <DialogTitle>Edit Environment</DialogTitle>
        <DialogContent sx={{display: 'flex', flexDirection: 'column', gap: 2, mt: 1}}>
            <TextField label="Name" value={localEnv.name || ''} disabled fullWidth/>
            <TextField
                label="Owner"
                value={localEnv.owner || ''}
                onChange={e => setLocalEnv(prevState => ({...prevState, owner: e.target.value}))}
                fullWidth
            />
            <Select
                value={localEnv.status || ''}
                onChange={e => setLocalEnv(prev => ({...prev, status: e.target.value as EnvironmentStatus}))}
                fullWidth
            >
                {ALL_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </Select>
            <TextField
                label="Description"
                value={localEnv.description || ''}
                onChange={e => setLocalEnv(prev => ({...prev, description: e.target.value}))}
                fullWidth
            />
        </DialogContent>
        <DialogActions>
            <Button onClick={onClose} color="secondary">Close</Button>
            <Button onClick={handleSubmit} color="primary">Save Changes</Button>
        </DialogActions>
    </Dialog>
}
